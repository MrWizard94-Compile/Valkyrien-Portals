package com.valkyrienportals.transit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.core.internal.ShipTeleportData;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

/**
 * Carries a Valkyrien Skies ship through an Immersive Portals portal.
 *
 * <p>VS already ships the migration engine — {@code VsiServerShipWorld.teleportShip(ship, ShipTeleportData)}
 * where the data carries destination position, rotation, velocity and angular velocity — but its own portal
 * driver ({@code MixinMinecraftServer.handleShipPortals}) is disabled in this VS build and keyed on vanilla
 * {@code Blocks.NETHER_PORTAL}, which Immersive Portals replaces with its own {@code Portal} entities. This
 * handler supplies the missing bridge: each server tick it finds loaded ships sitting on an IP portal and
 * teleports them through it, transforming the ship's transform <em>and its momentum</em> by the portal's
 * rotation so the craft arrives oriented and moving rather than dead or sideways.
 *
 * <p>{@code transformPoint} places the ship right at the linked exit portal, so a naive timer cooldown lets
 * the ship ping-pong straight back — and the rapid re-teleport fires client ship-create packets faster than
 * the client can unload the ship from the previous dimension ("Received ship create packet for already
 * loaded ship?!"), which makes the ship's blocks fail to materialise and vanish. Instead each ship is
 * <em>disarmed</em> the moment it transits and only re-armed once it has cleared every portal — one transit
 * per approach, no bounce.
 *
 * <p>Plain Forge {@code ServerTickEvent} subscriber — no mixin, auto-registered via
 * {@code @Mod.EventBusSubscriber}.
 */
@Mod.EventBusSubscriber(modid = "valkyrienportals", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalShipTransit {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ship-centre distance (blocks) to the portal surface that triggers a transit. */
    private static final double TRANSIT_DISTANCE = 4.0;
    /** A disarmed ship re-arms once it is at least this far (blocks) from every portal. */
    private static final double REARM_DISTANCE = 6.0;
    /** Search radius (blocks) for nearby portals around a ship. */
    private static final double SEARCH_RANGE = 8.0;
    /** Heartbeat log interval (ticks). */
    private static final int HEARTBEAT_TICKS = 100;

    /** Ship ids that have transited and have not yet cleared the exit portal (must not re-transit). */
    private static final Set<Long> DISARMED = new HashSet<>();
    private static int heartbeat;

    private PortalShipTransit() {}

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        final Map<String, ServerLevel> levelsByDim = new HashMap<>();
        for (final ServerLevel level : server.getAllLevels()) {
            levelsByDim.put(VSGameUtilsKt.getDimensionId(level), level);
        }

        final VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(server);
        final List<LoadedServerShip> ships = new ArrayList<>();
        shipWorld.getLoadedShips().forEach(ships::add);

        if (++heartbeat >= HEARTBEAT_TICKS) {
            heartbeat = 0;
            LOGGER.info("[VP-TRANSIT] alive: loadedShips={} disarmed={}", ships.size(), DISARMED.size());
        }

        for (final LoadedServerShip ship : ships) {
            final long shipId = ship.getId();
            final ServerLevel level = levelsByDim.get(ship.getChunkClaimDimension());
            if (level == null) {
                continue;
            }

            final Vector3dc shipPosVs = ship.getTransform().getPositionInWorld();
            final Vec3 shipPos = new Vec3(shipPosVs.x(), shipPosVs.y(), shipPosVs.z());

            final List<Portal> nearby = IPMcHelper.getNearbyPortals(level, shipPos, SEARCH_RANGE)
                .collect(Collectors.toList());
            if (nearby.isEmpty()) {
                DISARMED.remove(shipId); // clear of all portals — ready to transit again
                continue;
            }

            Portal nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (final Portal p : nearby) {
                final double d = p.getDistanceToNearestPointInPortal(shipPos);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = p;
                }
            }

            if (DISARMED.contains(shipId)) {
                if (nearestDist > REARM_DISTANCE) {
                    DISARMED.remove(shipId); // moved clear of the exit portal — re-arm
                }
                continue;
            }

            if (nearest == null || nearest.getDestDim() == null || nearestDist > TRANSIT_DISTANCE) {
                continue;
            }
            final ServerLevel destLevel = server.getLevel(nearest.getDestDim());
            if (destLevel == null) {
                continue;
            }

            shipWorld.teleportShip(ship, buildTeleportData(ship, nearest, destLevel));
            DISARMED.add(shipId);
            LOGGER.info("[VP-TRANSIT] TELEPORTED ship={} {} -> {} (dist={})",
                shipId, ship.getChunkClaimDimension(), VSGameUtilsKt.getDimensionId(destLevel),
                String.format("%.2f", nearestDist));
        }
    }

    private static ShipTeleportData buildTeleportData(final LoadedServerShip ship, final Portal portal,
                                                      final ServerLevel destLevel) {
        final Vector3dc shipPosVs = ship.getTransform().getPositionInWorld();
        final Vec3 destPosMc = portal.transformPoint(new Vec3(shipPosVs.x(), shipPosVs.y(), shipPosVs.z()));
        final Vector3dc destPos = new Vector3d(destPosMc.x, destPosMc.y, destPosMc.z);

        // Compose the portal rotation onto the ship's current orientation.
        final DQuaternion pr = portal.getRotationD();
        final Quaterniondc portalRot = new Quaterniond(pr.getX(), pr.getY(), pr.getZ(), pr.getW());
        final Quaterniondc destRot = new Quaterniond(portalRot).mul(ship.getTransform().getShipToWorldRotation());

        // Rotate the linear and angular velocity through the portal so the craft keeps its momentum.
        final Vector3dc destVel = rotate(portal, ship.getVelocity());
        final Vector3dc destOmega = rotate(portal, ship.getAngularVelocity());

        return new ShipTeleportDataImpl(
            destPos, destRot, destVel, destOmega, VSGameUtilsKt.getDimensionId(destLevel), null, null);
    }

    private static Vector3dc rotate(final Portal portal, final Vector3dc vec) {
        final Vec3 rotated = portal.getRotationD().rotate(new Vec3(vec.x(), vec.y(), vec.z()));
        return new Vector3d(rotated.x, rotated.y, rotated.z);
    }
}

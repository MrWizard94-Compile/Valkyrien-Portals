package com.valkyrienportals.transit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Plain Forge {@code ServerTickEvent} subscriber — no mixin, auto-registered via
 * {@code @Mod.EventBusSubscriber}. A short per-ship cooldown stops the craft from bouncing back through the
 * exit portal. While the feature is in development it logs {@code [VP-TRANSIT]} diagnostics so misfires can
 * be diagnosed from the server log.
 */
@Mod.EventBusSubscriber(modid = "valkyrienportals", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalShipTransit {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How close (blocks) the ship centre must be to the portal surface to transit. */
    private static final double TRANSIT_DISTANCE = 4.0;
    /** Search radius (blocks) for nearby portals around a ship. */
    private static final double SEARCH_RANGE = 8.0;
    /** Ticks a ship is immune to re-transit after teleporting (prevents bounce-back). */
    private static final int COOLDOWN_TICKS = 60;
    /** Heartbeat log interval (ticks). */
    private static final int HEARTBEAT_TICKS = 40;

    private static final Map<Long, Integer> COOLDOWNS = new HashMap<>();
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

        COOLDOWNS.entrySet().removeIf(entry -> {
            final int remaining = entry.getValue() - 1;
            entry.setValue(remaining);
            return remaining <= 0;
        });

        final Map<String, ServerLevel> levelsByDim = new HashMap<>();
        for (final ServerLevel level : server.getAllLevels()) {
            levelsByDim.put(VSGameUtilsKt.getDimensionId(level), level);
        }

        final VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(server);
        final List<LoadedServerShip> ships = new ArrayList<>();
        shipWorld.getLoadedShips().forEach(ships::add);

        if (++heartbeat >= HEARTBEAT_TICKS) {
            heartbeat = 0;
            LOGGER.info("[VP-TRANSIT] alive: loadedShips={}", ships.size());
        }

        for (final LoadedServerShip ship : ships) {
            final long shipId = ship.getId();
            if (COOLDOWNS.containsKey(shipId)) {
                continue;
            }
            final ServerLevel level = levelsByDim.get(ship.getChunkClaimDimension());
            if (level == null) {
                continue;
            }

            final Vector3dc shipPosVs = ship.getTransform().getPositionInWorld();
            final Vec3 shipPos = new Vec3(shipPosVs.x(), shipPosVs.y(), shipPosVs.z());

            final List<Portal> nearby = IPMcHelper.getNearbyPortals(level, shipPos, SEARCH_RANGE)
                .collect(Collectors.toList());
            if (nearby.isEmpty()) {
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

            final boolean transitable = nearest != null && nearest.getDestDim() != null
                && nearestDist <= TRANSIT_DISTANCE;
            LOGGER.info("[VP-TRANSIT] ship={} dim={} pos=({}, {}, {}) portalsNear={} nearestDist={} destDim={} willTransit={}",
                shipId, ship.getChunkClaimDimension(),
                String.format("%.1f", shipPos.x), String.format("%.1f", shipPos.y), String.format("%.1f", shipPos.z),
                nearby.size(), String.format("%.2f", nearestDist),
                nearest == null ? "null" : nearest.getDestDim(), transitable);

            if (!transitable) {
                continue;
            }

            final ServerLevel destLevel = server.getLevel(nearest.getDestDim());
            if (destLevel == null) {
                LOGGER.warn("[VP-TRANSIT] ship={} destLevel for {} is not loaded; skipping", shipId, nearest.getDestDim());
                continue;
            }

            final ShipTeleportData data = buildTeleportData(ship, nearest, destLevel);
            shipWorld.teleportShip(ship, data);
            COOLDOWNS.put(shipId, COOLDOWN_TICKS);
            LOGGER.info("[VP-TRANSIT] TELEPORTED ship={} to dim={}", shipId, VSGameUtilsKt.getDimensionId(destLevel));
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

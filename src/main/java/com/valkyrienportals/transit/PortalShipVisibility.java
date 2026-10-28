package com.valkyrienportals.transit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.internal.world.VsiPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Makes Valkyrien Skies ships in a <em>remote</em> dimension trackable by players looking at that
 * dimension through an Immersive Portals portal — the server half of KNOWN_ISSUES #0.
 *
 * <p><b>Measured root cause (probes, 2026-06-16):</b> VS's per-player ship tracking is derived from
 * chunk watching, and the chunk-watch computation hard-gates on
 * {@code ship.chunkClaimDimension == player.dimension} before any other check — so the server never
 * sends {@code PacketShipDataCreate}/transform updates for another dimension's ships, and the client
 * has literally nothing to render through the portal (branch B of the ShipDataProbe decision matrix).
 *
 * <p><b>The data plane already works:</b> VS's own {@code MixinIpNewChunkTrackingGraph} feeds shipyard
 * chunks of ships near a portal destination into IP's cross-dimension chunk sync, so block data and
 * block updates for remote ships reach the correct remote {@code ClientLevel} through IP's wrapped
 * packets. Only the ship <em>metadata</em> path (create/remove/transform packets — all VS-custom,
 * dimension-tagged, safe cross-dimension) is blocked by the tracker gate.
 *
 * <p><b>This class</b> computes, a few times a second, which remote ships each player can plausibly
 * see: for every IP portal near the player whose destination is another dimension, ships intersecting
 * a box around the portal's exit. Those ship ids are (a) published to
 * {@link #seesShip(UUID, long)} for {@code MixinVsCoreChunkTrackerPortalDims}, which relaxes the
 * tracker's dimension gate for exactly these (player, ship) pairs, and (b) added to the player's
 * {@link MinecraftPlayer#getForceWatchingShips() force-watching set}, which the tracker consults in
 * place of its distance check — necessary because the player's position is in another dimension's
 * coordinate space, so the distance test can never pass. Both marks are withdrawn symmetrically when
 * the player or the ship leaves the portal neighbourhood, which routes the tracker into its natural
 * unwatch branch and sends the client a regular {@code PacketShipRemove}.
 *
 * <p>The vanilla chunk-tracking side effects of cross-dimension watching are suppressed by
 * {@code MixinChunkMapCrossDimGuard} and {@code MixinChunkManagementUntrackGuard} — vanilla chunk
 * packets carry no dimension context, so letting VS route them to a cross-dimension player would
 * corrupt the player's <em>current</em> level with the remote shipyard's chunk data (ship chunk
 * claims are allocated per dimension and can overlap numerically). IP's chunk sync already delivers
 * the same data with correct dimension routing, so nothing is lost by suppressing the vanilla path.
 */
@Mod.EventBusSubscriber(modid = "valkyrienportals", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalShipVisibility {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Portals within this range of a player are considered "being looked through" (blocks). */
    private static final double PORTAL_SCAN_RANGE = 48.0;
    /** Ships whose AABB intersects a box this size around the portal exit are made visible (blocks). */
    private static final double SHIP_VISIBLE_RANGE = 96.0;
    /** Recompute interval (ticks) — visibility is a coarse gate, not a per-frame decision. */
    private static final int RECOMPUTE_TICKS = 10;

    /** player uuid → ids of remote-dimension ships that player may currently see through portals. */
    private static final Map<UUID, Set<Long>> VISIBLE = new ConcurrentHashMap<>();
    private static int cooldown;

    private PortalShipVisibility() {}

    /**
     * Tracker-gate query used by {@code MixinVsCoreChunkTrackerPortalDims}. Runs on the server game
     * thread during VS's chunk-watch computation; the map is swapped atomically per entry.
     */
    public static boolean seesShip(final UUID playerUuid, final long shipId) {
        final Set<Long> ships = VISIBLE.get(playerUuid);
        return ships != null && ships.contains(shipId);
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++cooldown < RECOMPUTE_TICKS) {
            return;
        }
        cooldown = 0;
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        // Bucket every loaded ship by dimension once; per-player scans then reuse the buckets.
        final Map<String, List<LoadedServerShip>> shipsByDim = new HashMap<>();
        VSGameUtilsKt.getShipObjectWorld(server).getLoadedShips().forEach(ship ->
            shipsByDim.computeIfAbsent(ship.getChunkClaimDimension(), d -> new ArrayList<>()).add(ship));

        final Map<UUID, Set<Long>> computed = new HashMap<>();
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            final Set<Long> shipIds = remoteShipsVisibleTo(server, player, shipsByDim);
            if (!shipIds.isEmpty()) {
                computed.put(player.getUUID(), shipIds);
            }
        }

        // Publish to the tracker gate and mirror into VS's per-player force-watching sets. The
        // force-watching set is shared mutable state owned by VS (game thread), so only ids this
        // class previously added are ever removed — tracked implicitly as (old VISIBLE ∖ new).
        for (final VsiPlayer vsiPlayer : VSGameUtilsKt.getShipObjectWorld(server).getPlayers()) {
            if (!(vsiPlayer instanceof MinecraftPlayer mcPlayer)) {
                continue;
            }
            final UUID uuid = vsiPlayer.getUuid();
            final Set<Long> now = computed.getOrDefault(uuid, Set.of());
            final Set<Long> before = VISIBLE.getOrDefault(uuid, Set.of());
            if (now.equals(before)) {
                continue;
            }
            final HashSet<Long> forceWatching = mcPlayer.getForceWatchingShips();
            for (final Long stale : before) {
                if (!now.contains(stale)) {
                    forceWatching.remove(stale);
                }
            }
            forceWatching.addAll(now);
            LOGGER.debug("[VP-VISIBILITY] {} portal-visible remote ships: {}", uuid, now);
        }

        VISIBLE.keySet().retainAll(computed.keySet());
        VISIBLE.putAll(computed);
    }

    /** Ids of ships in other dimensions sitting near the exits of portals near this player. */
    private static Set<Long> remoteShipsVisibleTo(final MinecraftServer server, final ServerPlayer player,
                                                  final Map<String, List<LoadedServerShip>> shipsByDim) {
        final Set<Long> shipIds = new HashSet<>();
        final String playerDim = VSGameUtilsKt.getDimensionId(player.serverLevel());
        for (final Portal portal : IPMcHelper.getNearbyPortals(player.level(), player.position(), PORTAL_SCAN_RANGE)
                .collect(Collectors.toList())) {
            if (portal.getDestDim() == null) {
                continue;
            }
            final ServerLevel destLevel = server.getLevel(portal.getDestDim());
            if (destLevel == null) {
                continue;
            }
            final String destDim = VSGameUtilsKt.getDimensionId(destLevel);
            if (destDim.equals(playerDim)) {
                continue; // same-dimension portals need no help: native tracking already covers them
            }
            final List<LoadedServerShip> candidates = shipsByDim.get(destDim);
            if (candidates == null) {
                continue;
            }
            final Vec3 exit = portal.transformPoint(portal.getOriginPos());
            for (final LoadedServerShip ship : candidates) {
                final Vector3dc pos = ship.getTransform().getPositionInWorld();
                if (Math.abs(pos.x() - exit.x) <= SHIP_VISIBLE_RANGE
                        && Math.abs(pos.y() - exit.y) <= SHIP_VISIBLE_RANGE
                        && Math.abs(pos.z() - exit.z) <= SHIP_VISIBLE_RANGE) {
                    shipIds.add(ship.getId());
                }
            }
        }
        return shipIds;
    }
}

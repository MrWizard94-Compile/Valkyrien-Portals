package com.valkyrienportals.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.valkyrienportals.transit.PortalShipVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.impl.api.ServerShipInternal;
import org.valkyrienskies.core.impl.shadow.CY;
import org.valkyrienskies.core.internal.world.VsiPlayer;

/**
 * Relaxes vs-core's chunk-tracker dimension gate for (player, ship) pairs that
 * {@link PortalShipVisibility} has marked as visible through an Immersive Portals portal.
 *
 * <p>vs-core derives ship metadata networking (create / remove / transform packets) from chunk
 * watching, computed per active ship chunk in the shadow-obfuscated tracker
 * {@code org.valkyrienskies.core.impl.shadow.CY} (decompiled from {@code valkyrienskies-120-2.4.11};
 * its {@code toString} self-identifies as {@code ChunkTrackingInfo}). The per-chunk lambda body is:
 *
 * <pre>
 *   if (!Intrinsics.areEqual(ship.getChunkClaimDimension(), player.getDimension())) {
 *       if (!alreadyWatching) continue;   // cross-dimension: never START watching
 *       unwatch.add(player);              // cross-dimension: always STOP watching
 *       continue;
 *   }
 *   boolean force = player.getForceWatchingShips().contains(ship.getId());
 *   if (distance < watchRange || force) { watch... }
 * </pre>
 *
 * The dimension gate short-circuits <em>before</em> the force-watching check, so the force-watching
 * set alone cannot carry a ship across dimensions. This wrap intercepts the single
 * {@code VsiPlayer.getDimension()} call in that lambda: when the pair is portal-visible it answers
 * with the ship's own claim dimension, so the gate passes and control reaches the force-watching
 * check — which {@link PortalShipVisibility} has already primed. When visibility ends, the pair is
 * no longer overridden, the gate fails again, and the tracker's own unwatch branch sends the client
 * a regular {@code PacketShipRemove}: the full lifecycle stays inside VS's own state machine.
 *
 * <p>{@code remap = false}: shadow-obfuscated VS-core class, nothing SRG-mapped. The method selector
 * pins the exact descriptor read from the 2.4.11 jar via {@code javap}. {@code require = 0}: on any
 * other VS build the wrap silently does not apply and remote ships simply stay invisible (the
 * pre-fix behaviour) instead of crashing the game — this mod's public-distribution policy.
 */
@Mixin(value = CY.class, remap = false)
public abstract class MixinVsCoreChunkTrackerPortalDims {

    @WrapOperation(
        method = "a(Lorg/joml/primitives/AABBd;Lorg/joml/primitives/AABBic;"
            + "Lorg/valkyrienskies/core/api/world/LevelYRange;"
            + "Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;"
            + "Lorg/valkyrienskies/core/impl/shadow/CY;"
            + "Lorg/valkyrienskies/core/impl/api/ServerShipInternal;"
            + "Ljava/util/Set;Lorg/joml/Vector3d;DDLjava/util/TreeSet;Ljava/util/TreeSet;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/valkyrienskies/core/internal/world/VsiPlayer;getDimension()Ljava/lang/String;"
        ),
        require = 0)
    private static String vp$portalVisibleDimension(VsiPlayer player, Operation<String> original,
                                                    @Local(argsOnly = true) ServerShipInternal ship) {
        final String actual = original.call(player);
        if (PortalShipVisibility.seesShip(player.getUuid(), ship.getId())) {
            // Answer with the ship's own dimension so the tracker's equality gate passes; the
            // force-watching check right after it then admits the ship without a distance match.
            return ship.getChunkClaimDimension();
        }
        return actual;
    }
}

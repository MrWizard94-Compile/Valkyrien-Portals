package com.valkyrienportals.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.events.ShipUnloadEventClient;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck;

/**
 * Stops the {@code ClassCastException} that Valkyrien Skies throws on every dimension transit when
 * Immersive Portals is installed.
 *
 * <p>VS's client ship-unload handler (the synthetic lambda {@code ValkyrienSkiesMod.init$lambda$17},
 * decompiled from {@code valkyrienskies-120-2.4.11.jar}) is, in Kotlin source:
 * <pre>
 *   val level = Minecraft.getInstance().level
 *   if (level != null) {
 *       (level.chunkSource as ClientChunkCacheDuck).`vs$removeShip`(event.ship)   // line 170
 *   }
 *   val player = Minecraft.getInstance().player
 *   if (player is PlayerKnownShipsDuck) player.vs_removeKnownShip(event.ship.id)    // lines 173-174
 * </pre>
 *
 * <p>{@code level.chunkSource as ClientChunkCacheDuck} compiles to a {@code checkcast}. IP replaces
 * {@link ClientLevel#getChunkSource()} with its own {@code qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap},
 * which does NOT carry VS's {@link ClientChunkCacheDuck} mixin (that mixin targets vanilla
 * {@code ClientChunkCache}). The cast therefore throws on every OW&harr;Nether (or any portal) transit,
 * and — because it throws at line 170 — it also aborts the unrelated player-side known-ship removal at
 * lines 173-174.
 *
 * <p>This wrap skips ONLY the chunk-source branch when IP owns the chunk source: there is no VS-aware
 * ship-chunk tracking on {@code ImmPtlClientChunkMap} to remove, so skipping it is correct, not a
 * band-aid. The player-side {@link PlayerKnownShipsDuck#vs_removeKnownShip(long)} removal is preserved
 * by replicating it here. When the chunk source is the vanilla/VS-owned cache (a {@link ClientChunkCacheDuck}),
 * or there is no level, VS's original runs unchanged.
 *
 * <p>{@code remap = false}: the target is a VS class and a synthetic lambda whose name is not SRG-mapped.
 * The selector pins both name and descriptor. {@code require = 0} makes this a <em>best-effort</em> compat
 * guard tuned to {@code valkyrienskies-120-2.4.11}: on a VS build whose synthetic lambda index has shifted,
 * the wrap simply does not apply (it no-ops) instead of aborting class load. The unguarded dimension-transit
 * CCE would return on such a build — recoverable log noise — whereas a hard mixin-apply failure would crash
 * the game outright. Graceful degradation is the safer default for public distribution.
 */
@Mixin(value = ValkyrienSkiesMod.class, remap = false)
public abstract class MixinValkyrienSkiesModShipUnloadGuard {

    @WrapMethod(
        method = "init$lambda$17(Lorg/valkyrienskies/core/api/events/ShipUnloadEventClient;)Lkotlin/Unit;",
        require = 0)
    private static Unit vp$tolerateImmersivePortalsChunkSource(ShipUnloadEventClient event,
                                                               Operation<Unit> original) {
        ClientLevel level = Minecraft.getInstance().level;
        // VS-owned cache (or no level): the cast in VS's original is safe — run it untouched.
        if (level == null || level.getChunkSource() instanceof ClientChunkCacheDuck) {
            return original.call(event);
        }

        // IP owns the chunk source (ImmPtlClientChunkMap). It holds no VS ship-chunk tracking, so the
        // chunk-source removal VS attempts has nothing to do and its cast would CCE. Skip only that
        // branch; still drop the ship from the player's known-ship set, which VS's original does
        // independently and which the CCE otherwise prevents.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player instanceof PlayerKnownShipsDuck knownShips) {
            knownShips.vs_removeKnownShip(event.getShip().getId());
        }
        return Unit.INSTANCE;
    }
}

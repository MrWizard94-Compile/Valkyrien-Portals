package com.valkyrienportals.mixin.common;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses vanilla chunk tracking of a player who is not in this {@code ChunkMap}'s dimension.
 *
 * <p>Vanilla never does this — every vanilla caller of {@code updateChunkTracking} passes players of
 * the map's own level, and Immersive Portals syncs remote-dimension chunks through its own
 * dimension-tagged packet path, not this method. The only cross-dimension caller in this pack is
 * Valkyrien Skies' {@code ChunkManagement.tickChunkLoading}, which executes ship-chunk watch tasks
 * by invoking this method (via accessor) on the <em>ship's</em> level for every watching player —
 * legitimate today only because VS's tracker guarantees watchers share the ship's dimension, a
 * guarantee {@code MixinVsCoreChunkTrackerPortalDims} deliberately relaxes for portal-visible ships
 * (VS itself logs "Player received watch task for chunk in dimension that they are not also in!"
 * for this case rather than preventing it).
 *
 * <p>Letting the call through would send {@code ClientboundLevelChunkWithLightPacket}s — which carry
 * no dimension context — to a player whose client applies them to their <em>current</em> level. Ship
 * chunk claims are allocated per dimension and can overlap numerically, so the remote shipyard's
 * blocks would be written over a same-dimension ship's client chunks. The remote ship's actual block
 * data still arrives correctly: VS's {@code MixinIpNewChunkTrackingGraph} routes shipyard chunks
 * through IP's cross-dimension sync, which wraps packets with their dimension.
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMapCrossDimGuard {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void vp$refuseCrossDimensionTracking(ServerPlayer player, ChunkPos chunkPos,
                                                 MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
                                                 boolean wasLoaded, boolean load, CallbackInfo ci) {
        if (player.serverLevel() != this.level) {
            ci.cancel();
        }
    }
}

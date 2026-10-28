package com.valkyrienportals.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.internal.world.chunks.VsiChunkUnwatchTask;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.ChunkManagement;

/**
 * The unwatch-side twin of {@link MixinChunkMapCrossDimGuard}.
 *
 * <p>When a portal-visible remote ship leaves a player's tracking set (portal closed, player or ship
 * moved away), VS's {@code ChunkManagement.tickChunkLoading} executes the unwatch task by calling
 * {@code ServerPlayer.untrackChunk} (SRG {@code m_9088_}) for every previously watched ship chunk.
 * That sends {@code ClientboundForgetLevelChunkPacket} — dimension-blind, like all vanilla chunk
 * packets — which the client applies to its <em>current</em> level, dropping a same-dimension ship's
 * chunk whenever the two dimensions' shipyard claims overlap numerically.
 *
 * <p>This wrap forwards the call only when the task's dimension matches the player's; cross-dimension
 * unwatches are dropped. Nothing needs forgetting on the client for those: the remote shipyard chunks
 * were delivered through IP's chunk sync, and IP's own tracking graph unloads them when the portal
 * stops loading them (VS's {@code MixinIpNewChunkTrackingGraph} feeds ship chunks into that same
 * lifecycle).
 *
 * <p>{@code remap = false}: the target is a VS mod class, so the method selector is not SRG-mapped;
 * the {@code At} target therefore names the production (SRG) form of {@code untrackChunk} directly,
 * matching the bytecode of {@code valkyrienskies-120-2.4.11}. {@code require = 0}: best-effort on
 * other VS builds, per this mod's graceful-degradation policy.
 */
@Mixin(value = ChunkManagement.class, remap = false)
public abstract class MixinChunkManagementUntrackGuard {

    @WrapOperation(
        method = "tickChunkLoading",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;m_9088_(Lnet/minecraft/world/level/ChunkPos;)V"
        ),
        require = 0)
    private static void vp$skipCrossDimensionUntrack(ServerPlayer player, ChunkPos chunkPos,
                                                     Operation<Void> original,
                                                     @Local VsiChunkUnwatchTask task) {
        if (task.getDimensionId().equals(VSGameUtilsKt.getDimensionId(player.serverLevel()))) {
            original.call(player, chunkPos);
        }
    }
}

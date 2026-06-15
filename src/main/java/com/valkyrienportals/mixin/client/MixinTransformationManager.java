package com.valkyrienportals.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.TransformationManager;

/**
 * Diagnostic instrument for the "portal pane blanks while riding a VS ship" bug.
 *
 * <p>Immersive Portals applies its camera matrix in
 * {@link TransformationManager#processTransformation(Camera, PoseStack)} every frame. We tap that
 * point and log — unconditionally on a throttle, plus immediately whenever ride-state flips — the
 * player position, ride state, ridden vehicle, dimension, camera position, and IP's current pose
 * rotation.
 *
 * <p>The hypothesis: mounting a Valkyrien Skies ship relocates the player's effective position into
 * the ship's shipyard (far from the world), so IP culls the world portal by distance. If the logged
 * positions jump to huge coordinates the instant you sit down, that is confirmed. Logging is
 * unconditional (not gated on any ship query) so this also proves whether the hook fires at all —
 * the previous build's silence was a broken ship test, not necessarily a dead hook.
 *
 * <p>{@code remap = false} because the target is Immersive Portals' own class; vanilla member calls
 * in the handler body are still reobfuscated normally.
 */
@Mixin(value = TransformationManager.class, remap = false)
public abstract class MixinTransformationManager {

    private static final Logger VP_LOGGER = LogUtils.getLogger();

    private static boolean vp$announced = false;
    private static boolean vp$wasRiding = false;

    @Inject(method = "processTransformation", at = @At("TAIL"))
    private static void vp$measure(Camera camera, PoseStack poseStack, CallbackInfo ci) {
        if (!vp$announced) {
            vp$announced = true;
            VP_LOGGER.info("[VP] processTransformation hook is live");
        }

        Player player = Minecraft.getInstance().player;
        boolean riding = player != null && player.isPassenger();

        // Heartbeat removed: this seam is exonerated (the camera transform is healthy while riding).
        // Keep only ride-state transitions, to cross-reference with MixinPortalRenderer's readout.
        boolean rideChanged = riding != vp$wasRiding;
        vp$wasRiding = riding;
        if (!rideChanged) {
            return;
        }

        Vec3 cam = camera.getPosition();
        Matrix4f p = poseStack.last().pose();
        String playerPos = (player == null) ? "no-player"
                : String.format("(%.1f, %.1f, %.1f)", player.getX(), player.getY(), player.getZ());
        Entity vehicleEntity = (player == null) ? null : player.getVehicle();
        String vehicle = (vehicleEntity == null) ? "none" : vehicleEntity.getClass().getName();
        String dim = (player == null) ? "?" : player.level().dimension().location().toString();

        VP_LOGGER.info(String.format(
            "[VP] %s | riding=%s vehicle=%s | dim=%s | playerPos=%s | camPos=(%.1f, %.1f, %.1f) "
                + "| poseRot=[%.3f %.3f %.3f / %.3f %.3f %.3f / %.3f %.3f %.3f]",
            "RIDE-CHANGE",
            riding, vehicle, dim, playerPos,
            cam.x, cam.y, cam.z,
            p.m00(), p.m01(), p.m02(),
            p.m10(), p.m11(), p.m12(),
            p.m20(), p.m21(), p.m22()));
    }
}

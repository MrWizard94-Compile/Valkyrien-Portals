package com.valkyrienportals.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * The render bridge for "portal pane blanks while riding a Valkyrien Skies ship".
 *
 * <p>Measured root cause (instrumented + bisected): Valkyrien Skies wraps the
 * {@code LevelRenderer.prepareCullFrustum(...)} call inside {@link GameRenderer#renderLevel} with a
 * MixinExtras {@code @WrapOperation}; when the player is mounted to a ship it swaps in a ship-relative
 * camera, repositioning the render camera to the ship's world-space player eye. That wrap runs on
 * EVERY {@code renderLevel} — including the nested passes Immersive Portals issues to draw the view
 * through a portal. So during the nested pass IP's remote-dimension camera is clobbered back to the
 * ship position; the return portal then falls outside render range and the pane comes back empty.
 * Vanilla boats/minecarts are never ship-mounted, so VS never fires for them and they render through
 * portals correctly — the bisect that isolated this.
 *
 * <p>Fix: compose a higher-priority {@code @WrapOperation} on the same call. While IP is mid-portal
 * render ({@link PortalRendering#isRendering()}), bypass VS entirely and invoke vanilla
 * {@code prepareCullFrustum} directly, leaving IP's portal camera in place. On the main pass (not
 * rendering a portal) we defer to VS's wrap unchanged, so riding a ship looks exactly as before.
 *
 * <p>Priority 2000 (above VS's default 1000) makes this wrapper the OUTER one, so it can choose to
 * skip VS's inner wrap rather than being skipped by it. {@code remap} stays default (true): the
 * vanilla {@code renderLevel}/{@code prepareCullFrustum} selectors are written to the refmap as SRG.
 */
@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class MixinGameRendererPortalCamera {

    @WrapOperation(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum("
                + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"
        )
    )
    private void vp$bypassShipCameraInPortalPass(LevelRenderer levelRenderer, PoseStack poseStack,
                                                 Vec3 cameraPos, Matrix4f projection,
                                                 Operation<Void> original) {
        if (PortalRendering.isRendering()) {
            // Nested portal pass: IP has already placed the camera at the remote-dimension position.
            // Run the vanilla frustum prep with that camera, skipping VS's ship-relative reposition.
            ((LevelRendererPrepareCullFrustumInvoker) levelRenderer)
                .vp$invokePrepareCullFrustum(poseStack, cameraPos, projection);
        } else {
            // Main view: let VS's wrap run so ship riding renders ship-relative as usual.
            original.call(levelRenderer, poseStack, cameraPos, projection);
        }
    }
}

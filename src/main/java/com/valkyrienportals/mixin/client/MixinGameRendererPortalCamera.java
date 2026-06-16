package com.valkyrienportals.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * The render bridge for Immersive Portals panes while riding a Valkyrien Skies ship.
 *
 * <p>Measured root cause (instrumented + bisected): Valkyrien Skies wraps the
 * {@code LevelRenderer.prepareCullFrustum(...)} call inside {@link GameRenderer#renderLevel} with a
 * MixinExtras {@code @WrapOperation}; when the player is mounted to a ship it swaps in a ship-relative
 * camera, repositioning the render camera to the ship's world-space eye and banking the pose with the
 * ship. That wrap runs on EVERY {@code renderLevel} — including the nested passes IP issues to draw the
 * view through a portal — so during the nested pass IP's remote-dimension camera was clobbered back to
 * the ship position and the pane came back empty.
 *
 * <p><b>Position fix.</b> A higher-priority (2000 &gt; VS's 1000) {@code @WrapOperation} on the same
 * call: while IP is mid-portal render ({@link PortalRendering#isRendering()}), bypass VS and invoke
 * vanilla {@code prepareCullFrustum} directly, leaving IP's portal camera position in place. The main
 * pass defers to VS unchanged. {@code remap} stays default (true) so the vanilla selectors hit the SRG
 * refmap.
 *
 * <p><b>Ship-bank fix (2026-06-16, self-calibrating).</b> Bypassing VS keeps the portal camera
 * position correct, but VS also banks the MAIN view with the ship, and that bank is what draws the
 * portal <em>frame</em>. The nested content pass (IP's fresh look-only camera) is not banked, so on a
 * tilted ship the through-portal view is rolled relative to the frame. The bank VS applies is the
 * screen-space operator {@code B} with {@code MAIN_pose = B · L}, where {@code L} is the pre-VS look
 * pose — exactly what is on the stack when this wrap is entered, before {@code original.call} runs VS.
 * So each frame the main pass captures {@code L}, lets VS produce {@code MAIN}, and derives
 * {@code B = MAIN · L⁻¹}. For continuity the through-portal content must be the portal image of the
 * banked camera, i.e. {@code B · nested}, so the nested pass left-multiplies by {@code B}. This is
 * exact for any portal orientation (it is measured, not assumed) and collapses to identity whenever VS
 * does not bank — so unmounted, level-ship, and vanilla riders are untouched. The main pass always
 * runs before the nested passes within a frame, so {@code B} is current when applied.
 */
@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class MixinGameRendererPortalCamera {

    /** Screen-space ship bank captured from the most recent main pass: {@code B = MAIN · L⁻¹}. */
    private static final Matrix4f vp$shipBank = new Matrix4f();
    private static final Matrix3f vp$shipBankNormal = new Matrix3f();

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
            // Nested portal pass: IP placed the camera at the remote-dimension portal mirror with a
            // look-only orientation. Left-multiply by the ship bank captured from this frame's main
            // pass so the content stays seamless with the ship-tilted frame, then run vanilla frustum
            // prep with IP's camera (skipping VS's reposition, which blanks the pane).
            PoseStack.Pose pose = poseStack.last();
            pose.pose().mulLocal(vp$shipBank);
            pose.normal().mulLocal(vp$shipBankNormal);
            ((LevelRendererPrepareCullFrustumInvoker) levelRenderer)
                .vp$invokePrepareCullFrustum(poseStack, cameraPos, projection);
        } else {
            // Main view: capture the pre-VS look pose, let VS bank it, then derive B = MAIN · L⁻¹.
            Matrix4f lookPose = new Matrix4f(poseStack.last().pose());
            original.call(levelRenderer, poseStack, cameraPos, projection);
            vp$shipBank.set(poseStack.last().pose()).mul(lookPose.invert());
            vp$shipBankNormal.set(new Matrix3f(vp$shipBank));
        }
    }
}

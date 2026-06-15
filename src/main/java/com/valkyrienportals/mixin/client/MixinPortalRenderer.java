package com.valkyrienportals.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.PortalRenderer;
import qouteall.imm_ptl.core.render.PortalRenderable;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.List;

/**
 * Diagnostic instrument for the "portal pane blanks while riding a VS ship" bug — third pass.
 *
 * <p>The selection tap proved IP's {@link PortalRenderer#getPortalsToRender(PoseStack)} returns the
 * in-range portal while standing (count=1, d≈10) but excludes it while riding (count=0), at identical
 * distance and well inside {@code renderRange=192}. Reading the decompiled
 * {@code shouldSkipRenderingPortal}, the only riding-sensitive gate is
 * {@code isRoughlyVisibleTo(cameraPos) = getDistanceToPlane(cameraPos) > 0}, where {@code cameraPos}
 * is {@link TransformationManager#getIsometricAdjustedCameraPos()} — i.e. the render camera's
 * position, not the player entity's.
 *
 * <p>This pass logs that exact camera position. Hypothesis (the VS shipyard/world split): while the
 * player rides a Valkyrien Skies ship, the render camera is placed in shipyard coordinates for the
 * ship-relative pass, so {@code getDistanceToPlane} of the world-space portal plane is wrong and the
 * portal is culled. If {@code ipCam} reads shipyard-scale (huge) while riding, that is confirmed and
 * the fix is to evaluate portal visibility against the world-space camera position. We also log
 * {@link PortalRendering#isRendering()} ({@code rendering=false} = the main pass) to disambiguate the
 * nested portal-recursion passes from the main view.
 *
 * <p>{@code remap = false}: the target is Immersive Portals' own method; vanilla member calls in the
 * handler body still reobfuscate to SRG normally.
 */
@Mixin(value = PortalRenderer.class, remap = false)
public abstract class MixinPortalRenderer {

    private static final Logger VP_LOGGER = LogUtils.getLogger();

    private static boolean vp$selAnnounced = false;
    private static boolean vp$selWasPassenger = false;
    private static int vp$selCall = 0;

    @Inject(method = "getPortalsToRender", at = @At("RETURN"))
    private void vp$measureSelection(PoseStack poseStack,
                                     CallbackInfoReturnable<List<PortalRenderable>> cir) {
        if (!vp$selAnnounced) {
            vp$selAnnounced = true;
            VP_LOGGER.info("[VP-SEL] getPortalsToRender hook is live");
        }

        Minecraft mc = Minecraft.getInstance();
        Entity camEnt = mc.getCameraEntity();
        boolean passenger = camEnt != null && camEnt.isPassenger();

        // Log on passenger-state transitions immediately; otherwise throttle. getPortalsToRender is
        // called several times per frame (once per recursion level), so unthrottled logging floods.
        boolean passengerChanged = passenger != vp$selWasPassenger;
        vp$selWasPassenger = passenger;
        if (!passengerChanged && (vp$selCall++ % 60) != 0) {
            return;
        }

        List<PortalRenderable> portals = cir.getReturnValue();
        int count = (portals == null) ? -1 : portals.size();

        // The exact position IP culls portals against (TransformationManager.getIsometricAdjustedCameraPos,
        // which is camera.getPosition() in the normal non-isometric case).
        Vec3 ipCam = TransformationManager.getIsometricAdjustedCameraPos();
        boolean rendering = PortalRendering.isRendering();
        String dim = (mc.level == null) ? "?" : mc.level.dimension().location().toString();

        // The nested view's ORIENTATION. IP only moves the camera position + applies a portal pose
        // matrix; it never re-rotates the Camera for a rotationless portal. So any rotation VS bakes
        // in (shipRotation on a mounted ship) flows through the portal uncanceled. Log both the pose's
        // 3x3 (what actually orients the render) and the camera's forward/up vectors so a mounted vs
        // unmounted look-through at the same spot reveals the exact leaked transform.
        org.joml.Matrix4f pose = poseStack.last().pose();
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        org.joml.Vector3f fwd = cam.getLookVector();
        org.joml.Vector3f up = cam.getUpVector();

        StringBuilder detail = new StringBuilder();
        if (portals != null) {
            int shown = 0;
            for (PortalRenderable renderable : portals) {
                if (shown >= 3) {
                    detail.append(" ...");
                    break;
                }
                if (renderable instanceof Portal portal) {
                    double planeDist = portal.getDistanceToPlane(ipCam);
                    detail.append(String.format(" [#%d @ (%.1f,%.1f,%.1f) plane=%.1f]",
                            portal.getId(), portal.getX(), portal.getY(), portal.getZ(), planeDist));
                } else {
                    detail.append(" [").append(renderable.getClass().getSimpleName()).append(']');
                }
                shown++;
            }
        }

        VP_LOGGER.info(String.format(
            "[VP-SEL] %s | passenger=%s rendering=%s | dim=%s | ipCam=(%.1f, %.1f, %.1f) "
                + "| renderRange=%.1f | portalsToRender=%d%s | fwd=(%.2f,%.2f,%.2f) up=(%.2f,%.2f,%.2f) "
                + "poseRot=[%.2f %.2f %.2f / %.2f %.2f %.2f / %.2f %.2f %.2f]",
            passengerChanged ? "PASSENGER-CHANGE" : "sample",
            passenger, rendering, dim, ipCam.x, ipCam.y, ipCam.z,
            PortalRenderer.getRenderRange(), count, detail,
            fwd.x(), fwd.y(), fwd.z(), up.x(), up.y(), up.z(),
            pose.m00(), pose.m01(), pose.m02(),
            pose.m10(), pose.m11(), pose.m12(),
            pose.m20(), pose.m21(), pose.m22()));
    }
}

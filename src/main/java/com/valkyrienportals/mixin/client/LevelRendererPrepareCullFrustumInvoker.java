package com.valkyrienportals.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for the vanilla {@code LevelRenderer.prepareCullFrustum(PoseStack, Vec3, Matrix4f)} so the
 * render bridge ({@link MixinGameRendererPortalCamera}) can call it directly, bypassing Valkyrien
 * Skies' ship-camera {@code @WrapOperation}, during Immersive Portals' nested portal render.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererPrepareCullFrustumInvoker {

    @Invoker("prepareCullFrustum")
    void vp$invokePrepareCullFrustum(PoseStack poseStack, Vec3 cameraPos, Matrix4f projection);
}

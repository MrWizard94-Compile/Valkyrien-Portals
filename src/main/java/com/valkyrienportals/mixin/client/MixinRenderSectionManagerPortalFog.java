package com.valkyrienportals.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * Decouples Embeddium's section selection from the host pass's shader fog during Immersive Portals'
 * nested passes. See {@link MixinGameRendererPortalCamera} for the camera-side bypass.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManagerPortalFog {

    /**
     * During a nested portal pass, ignore the host pass's (possibly collapsed) shader fog-end so
     * Embeddium's fog-occlusion search distance resolves to the full render distance. Harmless outside
     * portal rendering.
     */
    @ModifyExpressionValue(
        method = "getEffectiveRenderDistance",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getShaderFogEnd()F"
        )
    )
    private float vp$ignoreFogOcclusionInPortalPass(float fogEnd) {
        return PortalRendering.isRendering() ? Float.MAX_VALUE : fogEnd;
    }
}

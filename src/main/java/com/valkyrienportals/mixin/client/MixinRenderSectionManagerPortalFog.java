package com.valkyrienportals.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * Diagnoses (and, via the fog override, partially decouples) Embeddium's section selection for
 * Immersive Portals' nested passes. See {@link MixinGameRendererPortalCamera} for the camera-side bypass.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManagerPortalFog {

    @Shadow
    public abstract int getVisibleChunkCount();

    /**
     * During a nested portal pass, ignore the host pass's (possibly collapsed) shader fog-end so
     * Embeddium's fog-occlusion search distance resolves to the full render distance. Harmless outside
     * portal rendering. (Did not, on its own, restore the mounted pane — kept as a correct decoupling.)
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

    // DIAGNOSTIC (throttled, portal-pass only): how many terrain sections the nested pane actually
    // collected. ~0 => the geometry is being CULLED out of the list; large => the geometry is in the
    // list but not rasterising (a depth/stencil/clip issue, i.e. the camera-clipping hypothesis).
    private static final Logger VP_SEC_LOG = LoggerFactory.getLogger("ValkyrienPortals-Sections");
    private static long vp$nextSecLog;

    @Inject(method = "createTerrainRenderList", at = @At("TAIL"))
    private void vp$logPortalSectionCount(CallbackInfo ci) {
        if (!PortalRendering.isRendering()) {
            return;
        }
        long now = System.nanoTime();
        if (now < vp$nextSecLog) {
            return;
        }
        vp$nextSecLog = now + 500_000_000L;
        LocalPlayer player = Minecraft.getInstance().player;
        boolean mounted = player != null && VSGameUtilsKt.getShipMountedToData(player, null) != null;
        VP_SEC_LOG.info("[VP-SEC] nested portal pass: visibleSections={} mounted={}",
            getVisibleChunkCount(), mounted);
    }
}

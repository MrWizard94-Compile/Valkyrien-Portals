package com.valkyrienportals.compat;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

/**
 * Resolves the boot crash between Valkyrien Skies 2 and Immersive Portals on Forge 1.20.1.
 *
 * <p>Both mods independently patch {@code Frustum.offsetToFullyIncludeCameraCube(int)} (SRG
 * {@code m_194441_}) to bound the vanilla frustum dead-loop that occurs when the camera sits far
 * from the world origin:
 * <ul>
 *   <li><b>Immersive Portals</b> {@code MixinFrustum_FixDeadLoop} {@code @Overwrite}s the whole
 *       method. Its replacement is the superset: an isometric-view early return, a 10-iteration
 *       cap on the {@code intersectAab} loop, and a rate-limited "projection matrix and frustum
 *       are abnormal" warning.</li>
 *   <li><b>Valkyrien Skies</b> {@code feature.fix_frustum_dead_loop.MixinFrustum} {@code @Inject}s
 *       a loop-counter guard at the {@code FrustumIntersection.intersectAab} call, at the same
 *       priority (1000).</li>
 * </ul>
 *
 * <p>Mixin refuses an equal-priority injection into a method another mixin has already merged, so
 * VS's {@code @Inject} (which is {@code require = 1}) fails with {@code InvalidInjectionException},
 * aborting the {@code CONSTRUCT} lifecycle and breaking every mod that touches {@code Frustum}.
 *
 * <p>The two fixes are functionally identical (both cap the loop at 10), so the cure is to drop one.
 * We cancel VS's redundant mixin and let IP's overwrite stand alone — chosen because IP's variant is
 * the superset and also carries IP-specific isometric handling that IP's own rendering relies on.
 *
 * <p>Discovered by MixinSquared via {@code META-INF/services}; MixinSquared's global
 * {@code ExtensionCancelApplication} removes the cancelled mixin from the target class context
 * before any mixin in that class is applied, so VS's {@code require} check never runs. This is
 * load-order independent — unlike a config-name or mixin-priority race, which cannot deterministically
 * slot a third mixin between two equal-priority ones.
 */
public final class DeadLoopFrustumCanceller implements MixinCanceller {

    private static final String VS_FRUSTUM_DEADLOOP_MIXIN =
            "org.valkyrienskies.mod.mixin.feature.fix_frustum_dead_loop.MixinFrustum";

    /** Resolved once: Immersive Portals supplies the surviving frustum overwrite. */
    private static final boolean IMMERSIVE_PORTALS_PRESENT = detectImmersivePortals();

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        // Only intervene when IP is actually present, so VS keeps its own dead-loop fix in packs
        // that do not run Immersive Portals.
        return IMMERSIVE_PORTALS_PRESENT && VS_FRUSTUM_DEADLOOP_MIXIN.equals(mixinClassName);
    }

    private static boolean detectImmersivePortals() {
        try {
            // LoadingModList is populated at mod-discovery time, well before mixins apply, so it is
            // safe to query from this early-instantiated service (ModList is not yet built here).
            return net.minecraftforge.fml.loading.LoadingModList.get()
                    .getModFileById("immersive_portals") != null;
        } catch (Throwable t) {
            // ValkyrienPortals declares Immersive Portals as a mandatory dependency, so it is present
            // whenever this canceller runs. If the FML lookup shape ever changes, assume present
            // rather than let the unhandled frustum collision crash the game.
            return true;
        }
    }
}

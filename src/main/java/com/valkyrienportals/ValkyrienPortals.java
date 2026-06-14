package com.valkyrienportals;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Compatibility patch making Valkyrien Skies 2 and Immersive Portals coexist on Forge 1.20.1.
 *
 * <p>The actual fix carries no mixins of its own: it ships a MixinSquared
 * {@link com.valkyrienportals.compat.DeadLoopFrustumCanceller} that cancels VS's redundant
 * {@code Frustum} dead-loop mixin so it stops colliding with Immersive Portals' overwrite. This
 * constructor only reports whether the MixinSquared runtime that drives that canceller is present.
 */
@Mod(ValkyrienPortals.MOD_ID)
public final class ValkyrienPortals {

    public static final String MOD_ID = "valkyrienportals";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ValkyrienPortals() {
        if (classPresent("com.bawnorton.mixinsquared.api.MixinCanceller")) {
            LOGGER.info("[ValkyrienPortals] Active. MixinSquared detected; the Valkyrien Skies frustum "
                    + "dead-loop mixin is cancelled in favour of Immersive Portals' overwrite.");
        } else {
            LOGGER.error("[ValkyrienPortals] MixinSquared is NOT on the classpath. The frustum-conflict "
                    + "canceller cannot run, so the Valkyrien Skies / Immersive Portals boot crash will "
                    + "occur. Install MixinSquared (bundled with Supplementaries) alongside this mod.");
        }
    }

    private static boolean classPresent(String binaryName) {
        try {
            Class.forName(binaryName, false, ValkyrienPortals.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

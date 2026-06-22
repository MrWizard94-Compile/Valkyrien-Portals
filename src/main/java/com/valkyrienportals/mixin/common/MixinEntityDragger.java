package com.valkyrienportals.mixin.common;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.EntityDragger;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Stops a Valkyrien Skies ship from dragging — and thereby breaking — an Immersive Portals portal.
 *
 * <p>An IP nether/end portal is a {@link Portal} entity (a render+logic entity, not a block). When a
 * VS ship moves into the portal, VS's per-entity dragging system treats the portal like any other
 * entity standing on the ship: {@code MixinEntity.postBaseTick} sets the portal's
 * {@code lastShipStoodOn}, and {@link EntityDragger#dragEntitiesWithShips} then translates and rotates
 * the portal with the ship (and {@code entity.push(...)} shoves it). The portal is yanked off its
 * obsidian frame, so IP's {@code BreakablePortalEntity.checkPortalIntegrity()} fails
 * {@code isPortalIntactOnThisSide()} on its next tick and calls {@code breakPortalOnThisSide()} — the
 * portal blocks are set to air and the portal entity is killed. Net effect the player sees: the craft
 * "pushes the pane" / the "pane becomes part of the craft", the portal breaks, and no transit happens.
 *
 * <p>VS already exposes the correct extension point for this: {@link EntityDragger#isDraggable(Entity)}
 * ("Shipyard entities and ones marked as non-draggable return false"). A portal must never be ship-
 * draggable, so we add IP {@code Portal} entities to that exclusion. Fixing the drag at the source
 * also removes the push (the {@code entity.push} call lives inside the drag branch) — root cause, not a
 * band-aid over IP's integrity check.
 *
 * <p>{@code remap = false}: the target is a VS class, not SRG-mapped. {@code require = 0}: a best-effort
 * compat guard — if a future VS build renames/removes {@code isDraggable} the mixin simply does not
 * apply (the drag-break returns) rather than failing class load, matching this mod's public-distribution
 * graceful-degradation policy.
 */
@Mixin(value = EntityDragger.class, remap = false)
public abstract class MixinEntityDragger {

    @Inject(method = "isDraggable", at = @At("HEAD"), cancellable = true, require = 0)
    private static void vp$immersivePortalsAreNotShipDraggable(Entity entity,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Portal) {
            cir.setReturnValue(false);
        }
    }
}

# Known Issues

Tracked defects observed while running the Base Wars pack (Valkyrien Skies 2 + Immersive
Portals + Valkyrien Portals on Forge 1.20.1). Each entry records the symptom, the current
root-cause hypothesis, who actually owns the bug, and whether Valkyrien Portals is involved.

Add new entries at the top. Keep "Owner" honest — most interaction bugs on ships live in VS or
its addons, not in this portal-compat mod.

---

## OPEN

### 1. Wanderlite wand cannot select blocks on a VS ship
- **Observed:** 2026-06-15, world `RenderTesting1`.
- **Symptom:** The vs_clockwork wanderlite wand does not select / register blocks that are part
  of an assembled Valkyrien Skies ship. Works on normal (world-space) blocks.
- **Root-cause hypothesis:** Selecting a block on a ship needs a ship-aware raycast
  (`org.valkyrienskies.mod.common.world.RaycastUtilsKt.clipIncludeShips`). If the wand uses a
  vanilla `Level.clip(...)` / `Entity.pick(...)` it traces only world space and misses the
  ship's shipyard-space geometry, so the hit is null/empty.
- **Owner:** **vs_clockwork** (VS addon). Fix = mixin into the wand's use/raycast path to route
  through `clipIncludeShips`. Not yet decompiled/confirmed.
- **Valkyrien Portals involvement:** NONE. This mod ships no raycast/interaction code — only the
  frustum-dead-loop canceller (render culling) and the IP portal-camera `prepareCullFrustum` wrap
  (rendering). Verified 2026-06-15.
- **Status:** Deferred. Tractable as a separate sub-project once the portal render bug is closed.

### 2. Create redstone links do not work on VS ships
- **Observed:** 2026-06-15, world `RenderTesting1`.
- **Symptom:** Create wireless redstone links placed on / assembled into a VS ship don't
  transmit as expected.
- **Root-cause hypothesis:** Create redstone links key their network on **world** `BlockPos`. A
  block assembled onto a ship physically lives in shipyard space (at the ship's real, far-from-
  origin coordinates), so the link's network identity and range checks don't match where the
  block visually appears. Known VS↔Create limitation; the network/frequency lookup is world-space.
- **Owner:** **Valkyrien Skies core / Create** interaction. Closest to VS-core; may not be fixable
  cleanly from a compat mod. Hardest of the tracked issues.
- **Valkyrien Portals involvement:** NONE (same verification as #1).
- **Status:** Deferred. Revisit only if a ship-space network-position shim is feasible.

---

## RESOLVED

_(none yet)_

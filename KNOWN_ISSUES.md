# Known Issues

Tracked defects observed while running the Base Wars pack (Valkyrien Skies 2 + Immersive
Portals + Valkyrien Portals on Forge 1.20.1). Each entry records the symptom, the current
root-cause hypothesis, who actually owns the bug, and whether Valkyrien Portals is involved.

Add new entries at the top. Keep "Owner" honest — most interaction bugs on ships live in VS or
its addons, not in this portal-compat mod.

---

## OPEN

### 0. VS ships in a remote dimension are invisible when viewed through an IP portal
- **Observed:** 2026-06-16, world `New World` (Base Wars), OW↔Nether intrinsic portal.
- **Symptom:** A VS ship sitting on the far side of a portal does not render through the portal.
- **Root cause (MEASURED 2026-06-16, supersedes the earlier "RSM never builds the ship sections"
  hypothesis, which is FALSIFIED):** the remote dimension's ships are never client-loaded while the
  player is in another dimension. The `[VP-SHIP]` probe (`MixinRenderSectionManagerShipProbe`, since
  stripped) walked every loaded ship at `createTerrainRenderList` TAIL in both the main and nested
  passes and logged dimension + section state. Findings:
  - When the RSM's dimension == the ship's dimension (`dimMatch=true`), the shipyard `RenderSection`s
    are present **and fully built in the nested pass too** (`sectionsNonNull=120 built=120`). So section
    building / the render pipeline is NOT the defect.
  - `getShipObjectWorld(mc).getLoadedShips()` only ever contains the ships of the dimension the player
    is **physically in**. In the overworld the list was {128,57,125,126,127} (all overworld); the nested
    pass rendering the nether iterated those same overworld ships at `dimMatch=false` with 0 sections.
    The nether ships (82,124) — sitting right at the nether portal mouth — were **absent from the list
    entirely** until the player physically crossed over, at which point the overworld ships were removed.
  - VS's client ship lifecycle is bolted to the player's current dimension (load-on-enter, remove-on-
    leave). IP rendering a second dimension's terrain does not bring that dimension's ships with it, so
    there is no ship object or shipyard chunk data on the client to draw.
- **MEASURED 2026-06-16 (ShipDataProbe, supersedes the client-load plan):** the `[VP-DATA]` probe logged
  `getAllShips()` vs `getLoadedShips()` bucketed by `getChunkClaimDimension()`. Steady-state result: the
  list only ever holds the player's **physical** dimension — `overworld{all=5,loaded=5}` while in the OW,
  `the_nether{all=2,loaded=2}` while in the nether. The remote dimension's ships are **absent from
  `getAllShips()` entirely** (`all[remoteDim] == 0`), not merely unloaded. Per the probe's own decision
  matrix this is **branch B: the server never sends the remote dimension's ships while the player is
  elsewhere.** The pure client-side shim (promote-to-loaded + load shipyard chunks) is therefore
  **FALSIFIED** — there is no client metadata to promote.
- **Owner:** **Valkyrien Portals** (compat seam), but the fix now needs **server cooperation**: track and
  send a portal-viewed dimension's ships to the client when IP is rendering that dimension. Larger than a
  client shim. Shares a root with the dimension-transit ClassCastException (`ImmPtlClientChunkMap` →
  `ClientChunkCacheDuck`, VS's `removeShip` on dim change), confirmed firing on **every** transit in this run.
- **Status:** Diagnosed and direction corrected.
  - (a) **DONE (compiles; pending in-game verify):** the per-transit CCE is guarded by
    `client.MixinValkyrienSkiesModShipUnloadGuard` — a MixinExtras `@WrapMethod` on VS's
    `ValkyrienSkiesMod.init$lambda$17` (the `ShipUnloadEventClient` handler). When IP owns the chunk
    source (`getChunkSource() !instanceof ClientChunkCacheDuck`) it skips only the chunk-source removal
    (nothing to clean there) and replicates the player-side `vs_removeKnownShip` that the CCE otherwise
    aborts. Plain `@Mixin` (the lambda is VS's own synthetic method, not another mod's handler), so no
    MixinSquared needed. Verify: portal-transit OW&harr;Nether, confirm the `removeShip` CCE spam is gone.
  - (b) **TODO:** scope the server-side remote-ship send (the actual invisible-ships fix).

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

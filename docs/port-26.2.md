# GitHub Actions validation

[English](port-26.2.md) · [Français](port-26.2.fr.md)

Compilation, automated tests and gameplay scenarios run exclusively on [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions). The workflow definition in [build.yml](../.github/workflows/build.yml) is the source of truth for commands, dependencies and timeouts. Do not run these checks locally.

## Build evidence

The `build` job checks out the pinned submodules, selects Java 25, builds the Fabric mod and internal libraries, executes JUnit tests, validates the access widener and compiles the opt-in smoke scenarios. Scenarios are not packaged in the distributed mod. Download artifacts from the successful run for the exact commit being reviewed.

The imported baseline has a [successful source-repository CI run](https://github.com/N4TH4NOT/immersive-portals-minestom/actions/runs/40115478061). Its workflow compiled the smoke scenarios but did not execute them. It is historical evidence, not a result for changes made here.

## Runtime evidence

The `fabric-runtime` matrix runs actual Minecraft clients against OpenGL and Vulkan under Xvfb, using Mesa software drivers. Its multiplayer, scaled-portal, rendering and singleplayer scenarios exercise dedicated/integrated servers, portal traversal, resources and rendering. Eight vanilla cases cover both backends; additional OpenGL cases exercise rendering with the pinned Sodium/Iris integrations and multiplayer movement with Sodium. The requested backend must be confirmed by the game log; a Vulkan fallback to OpenGL must fail the Vulkan check.

Success requires each scenario's completion marker, including `PORTAL_SMOKE_COMPLETE` or `PORTAL_RENDER_COMPLETE`, its specific server/resource markers, and the final `FABRIC_RUNTIME_VERIFIED` assertion. Dimension checks cover world seeds, codec round-trips and persistence after restarting with automatic defaults disabled. Rendering checks include screenshot pixel content, rather than treating a nonempty capture as proof that portals render correctly. A process merely starting is insufficient. Console logs, game logs, crash reports and captures are retained as workflow artifacts, including on failures.

Check both jobs for the same commit. A build alone cannot validate mixin application, world creation, dimensions, remote interaction or the rendered result. Inspect logs and screenshots before interpreting a runtime pass; automated scenes do not cover every feature, GPU or shaderpack. The pinned Sodium/Iris cases cover their fixed rendering fixtures; other versions, shaderpacks and external mods require separate compatibility evidence.

## Wall portals and slow movement

`RectangularPortalCrossingTest` covers resuming inward movement from exactly on the plane, stationary/away endpoints, reverse crossings and transformed apertures. These unit assertions run only on GitHub Actions; their success is separate from the near-camera GPU assertions.

The wall-rendering fixture keeps the stone backing wall and wand portal faces intact. Consecutive finalized GPU frames check visibility prediction, synchronous queries, a changed viewing angle, direct composition, a real foreground occluder and its removal. The destination's emerald wall is added during the scenario, so its appearance also exercises remote terrain updates. Near-camera series include eye-to-plane distances of `0.04`, `0.01` and `0.001` blocks, axial and 45-degree views, and direct composition. These image checks do not isolate every smooth-lighting calculation or replace compatibility testing of another mod.

The new `PortalSeamlessSmoke` regression drives ordinary movement keys: approach and stop before the plane, crouch until the body straddles it, stop, retreat, cross slowly, stop just beyond it and cross back. It observes real primary-view cameras, compares destination coordinates after transforming them back into source space, and checks bounded camera motion, direction, velocity and foot height. Stopped phases reject movement added to complete a crossing. `PORTAL_SMOKE_SEAMLESS_CAMERA_OK` reports camera checks; `PORTAL_SMOKE_SEAMLESS_MOVEMENT_OK` reports the controlled stop/reverse sequence. After the cross-world cycle, a real four-face wand pair translates the player 64 blocks within the Nether, over a distance of four chunks. The player walks to that second opening and repeats the stop/reverse sequence without a setup teleport. The observer requires the correct reverse-portal collision contact on the first rendered frame after each crossing. `PORTAL_SMOKE_SEAMLESS_SAME_WORLD_OK` and the matching server confirmation cover that phase. The rotated, scaled case also observes camera continuity during its existing crossing.

These describe the current regression implementation, pending validation of the containing revision on GitHub. Read its precise job results and retained traces before treating the source changes as a resolved movement or rendering issue.

## Review requirements

Record the exact commit, workflow URL, passing and failing jobs, and any unexercised features in the PR. Do not label the port complete while a required job fails or a known functional omission remains. See [migration findings](migration-audit-26.2.md) and [rendering design](rendering-26.2.md).

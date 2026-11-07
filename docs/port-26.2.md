# GitHub Actions validation

[English](port-26.2.md) · [Français](port-26.2.fr.md)

Compilation, automated tests and gameplay scenarios run exclusively on [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions). The workflow definition in [build.yml](../.github/workflows/build.yml) is the source of truth for commands, dependencies and timeouts. Do not run these checks locally.

## Build evidence

The `build` job checks out the pinned submodules, selects Java 25, builds the Fabric mod and internal libraries, executes JUnit tests, validates the access widener and compiles the opt-in smoke scenarios. Scenarios are not packaged in the distributed mod. Download artifacts from the successful run for the exact commit being reviewed.

The imported baseline has a [successful source-repository CI run](https://github.com/N4TH4NOT/immersive-portals-minestom/actions/runs/40115478061). Its workflow compiled the smoke scenarios but did not execute them. It is historical evidence, not a result for changes made here.

## Runtime evidence

The `fabric-runtime` matrix runs actual Minecraft clients against OpenGL and Vulkan under Xvfb, using Mesa software drivers. Its 20 configured runtime cases cover multiplayer, scaled portals, rendering, optics, native interaction, two-client quality and singleplayer. Vanilla scenarios cover both backends except the two-client quality case, which currently runs on OpenGL. Pinned Sodium cases cover rendering, optics, interaction, multiplayer and quality; pinned Iris cases cover rendering and optics on OpenGL. The requested backend must be confirmed by the game log; a Vulkan fallback to OpenGL must fail the Vulkan check. The expanded alpha.6 cases are pending validation; a configured job is not a passing result.

Success requires each scenario's completion marker, including `PORTAL_SMOKE_COMPLETE` or `PORTAL_RENDER_COMPLETE`, its specific server/resource markers, and its final CI assertion. Older scenarios report `FABRIC_RUNTIME_VERIFIED`; the new independent scenarios have the assertions described below. Dimension checks cover world seeds, codec round-trips and persistence after restarting with automatic defaults disabled. Rendering checks include screenshot pixel content, rather than treating a nonempty capture as proof that portals render correctly. A process merely starting is insufficient. Console logs, game logs, crash reports and captures are retained as workflow artifacts, including on failures.

Check both jobs for the same commit. A build alone cannot validate mixin application, world creation, dimensions, remote interaction or the rendered result. Inspect logs and screenshots before interpreting a runtime pass; automated scenes do not cover every feature, GPU or shaderpack. The pinned Sodium/Iris cases cover their fixed rendering fixtures; other versions, shaderpacks and external mods require separate compatibility evidence.

## Expanded visual and gameplay acceptance

The [seamless portal follow-up](seamless-26.2.md) records the alpha.6 implementation and remaining acceptance scope. Its independent scenarios supplement the earlier wall and movement checks:

| Scenario | Required evidence |
| --- | --- |
| `optics` | Consecutive pitched close views; source/destination occlusion and hidden-terrain sentinels; dark/lit/dark-again portal images compared with native destination views and real block-light values. |
| `rendering` | Existing depth/terrain/reload checks, followed by geometrically clipped body and third-person camera scenes; actual redirected audio routes, remote particle tick counts and destination environment observations. |
| `interaction` | 18 native-input phases with authoritative outcomes, including projected-body use/attack, normal bucket reach, a trade held open for 25 ticks then blocked, synchronized boat mounting and lethal Thorns damage/drops in the physical source world; outline on/off pixel differences and mining across a crossing. |
| `quality` | Two simultaneous distinct clients; opposite crossings and stopped cameras; remote pickup rules and animation; seven mob/item/projectile witnesses, including cross-dimension snowball/fireball native motion, impacts and identity preservation; saved-world and two-client restart; bounded Mesa frame/tick times. |
| `multiplayer` | Existing movement/reload checks plus seven actual-input sprint, crouch, fly, fall, swim, bow and food cases; independent client/server pose and camera observations; seven fluid-contact cases covering hidden water/lava, visible water, destination water with scale/rotation and destination lava current; bow release and food consumption after held use across dimensions. |

The dark-room comparison also requires destination client light data to agree with the server. `ClientLevel.update()` publishes pending light-engine sections; merely ticking an offscreen lightmap or polling incoming light packets does not establish that the underlying world values are current. The new pose/use, fluid-contact, projectile and interaction acceptance remains pending for the exact commit. The native projectile witnesses do not validate the inherited special paths for fishing hooks, fireworks, llama spit or shulker bullets; see the [precise implementation limits](seamless-26.2.md#fluid-contact-and-projectile-segments).

The quality scenario saves its own state in `portal-smoke-quality.json` and restarts the server and both clients. Its final assertion is `FABRIC_QUALITY_RESTART_VERIFIED`; the interaction and optics scenarios use their own completion assertions. Check those scripts and the scenario-specific markers rather than requiring only the generic marker used by older scenarios. Artifacts retain both client logs for quality, the snapshot, per-scenario summaries and captures on failure.

## Wall portals and slow movement

`RectangularPortalCrossingTest` covers resuming inward movement from exactly on the plane, stationary/away endpoints, reverse crossings and transformed apertures. These unit assertions run only on GitHub Actions; their success is separate from the near-camera GPU assertions.

The wall-rendering fixture keeps the stone backing wall and wand portal faces intact. Consecutive finalized GPU frames check visibility prediction, synchronous queries, a changed viewing angle, direct composition, a real foreground occluder and its removal. The destination's emerald wall is added during the scenario, so its appearance also exercises remote terrain updates. Near-camera series include eye-to-plane distances of `0.04`, `0.01`, `0.001` and `0.000001` blocks, axial and 45-degree views, and direct composition. The micro-distance probe requires the physical camera to remain in front within `1e-9` blocks of its target distance, so changing the camera position cannot substitute for fixing aperture rendering. These image checks do not isolate every smooth-lighting calculation or replace compatibility testing of another mod.

The new `PortalSeamlessSmoke` regression drives ordinary movement keys: approach and stop before the plane, crouch until the body straddles it, stop, retreat, cross slowly, stop just beyond it and cross back. It observes real primary-view cameras, compares destination coordinates after transforming them back into source space, and checks bounded camera motion, direction, velocity and foot height. Every stopped phase includes at least eight real extracted frames, so catching up simulation ticks cannot skip its camera checks. Smooth lighting is enabled and required by the observer. Stopped phases reject movement added to complete a crossing. `PORTAL_SMOKE_SEAMLESS_CAMERA_OK` reports camera checks; `PORTAL_SMOKE_SEAMLESS_MOVEMENT_OK` reports the controlled stop/reverse sequence. After the cross-world cycle, a real four-face wand pair translates the player 64 blocks within the Nether, over a distance of four chunks. The player walks to that second opening and repeats the stop/reverse sequence without a setup teleport. The observer requires the correct reverse-portal collision contact on the first rendered frame after each crossing. `PORTAL_SMOKE_SEAMLESS_SAME_WORLD_OK` and the matching server confirmation cover that phase. The rotated, scaled case also observes camera continuity during its existing crossing.

Use the GitHub Actions job results and retained traces for the matching commit to confirm these regressions. A successful build alone does not establish the movement or rendering behavior exercised by them.

## Review requirements

Record the exact commit, workflow URL, passing and failing jobs, and any unexercised features in the PR. Do not label the port complete while a required job fails or a known functional omission remains. See [migration findings](migration-audit-26.2.md) and [rendering design](rendering-26.2.md).

# GitHub Actions validation

[English](port-26.2.md) · [Français](port-26.2.fr.md)

Compilation, automated tests and gameplay scenarios run exclusively on [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions). The workflow definition in [build.yml](../.github/workflows/build.yml) is the source of truth for commands, dependencies and timeouts. Do not run these checks locally.

## Build evidence

The `build` job checks out the pinned submodules, selects Java 25, builds the Fabric mod and internal libraries, executes JUnit tests, validates the access widener and compiles the opt-in smoke scenarios. Scenarios are not packaged in the distributed mod. Download artifacts from the successful run for the exact commit being reviewed.

The imported baseline has a [successful source-repository CI run](https://github.com/N4TH4NOT/immersive-portals-minestom/actions/runs/40115478061). Its workflow compiled the smoke scenarios but did not execute them. It is historical evidence, not a result for changes made here.

## Runtime evidence

The `fabric-runtime` matrix runs actual Minecraft clients against OpenGL and Vulkan under Xvfb, using Mesa software drivers. Its multiplayer, scaled-portal, rendering and singleplayer scenarios exercise dedicated/integrated servers, portal traversal, resources and rendering. Eight vanilla cases cover both backends; two additional OpenGL rendering cases use the pinned Sodium and Iris integrations. The requested backend must be confirmed by the game log; a Vulkan fallback to OpenGL must fail the Vulkan check.

Success requires each scenario's completion marker, including `PORTAL_SMOKE_COMPLETE` or `PORTAL_RENDER_COMPLETE`, its specific server/resource markers, and the final `FABRIC_RUNTIME_VERIFIED` assertion. Dimension checks cover world seeds, codec round-trips and persistence after restarting with automatic defaults disabled. Rendering checks include screenshot pixel content, rather than treating a nonempty capture as proof that portals render correctly. A process merely starting is insufficient. Console logs, game logs, crash reports and captures are retained as workflow artifacts, including on failures.

Check both jobs for the same commit. A build alone cannot validate mixin application, world creation, dimensions, remote interaction or the rendered result. Inspect logs and screenshots before interpreting a runtime pass; automated scenes do not cover every feature, GPU or shaderpack. The pinned Sodium/Iris cases cover their fixed rendering fixtures; other versions, shaderpacks and external mods require separate compatibility evidence.

## Review requirements

Record the exact commit, workflow URL, passing and failing jobs, and any unexercised features in the PR. Do not label the port complete while a required job fails or a known functional omission remains. See [migration findings](migration-audit-26.2.md) and [rendering design](rendering-26.2.md).

# GitHub Actions validation

[English](port-26.2.md) · [Français](port-26.2.fr.md)

Compilation, automated tests and gameplay scenarios run exclusively on [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions). The workflow definition in [build.yml](../.github/workflows/build.yml) is the source of truth for commands, dependencies and timeouts. Do not run these checks locally.

## Build evidence

The `build` job checks out the pinned submodules, selects Java 25, builds the Fabric mod and internal libraries, executes JUnit tests, validates the access widener and compiles the opt-in smoke scenarios. Scenarios are not packaged in the distributed mod. Download artifacts from the successful run for the exact commit being reviewed.

The imported baseline has a [successful source-repository CI run](https://github.com/N4TH4NOT/immersive-portals-minestom/actions/runs/40115478061). Its workflow compiled the smoke scenarios but did not execute them. It is historical evidence, not a result for changes made here.

## Runtime evidence

The `fabric-runtime` matrix runs actual Minecraft clients against OpenGL and Vulkan under Xvfb, using Mesa software drivers. Its multiplayer, rendering and singleplayer scenarios exercise dedicated/integrated servers, portal traversal, resources and rendering. The requested backend must be confirmed by the game log; a Vulkan fallback to OpenGL must fail the Vulkan check.

Success requires each scenario's completion marker, including `PORTAL_SMOKE_COMPLETE` or `PORTAL_RENDER_COMPLETE`, plus its specific server/resource markers. A process merely starting is insufficient. Console logs, game logs, crash reports and captures are retained as workflow artifacts, including on failures.

Check both jobs for the same commit. A build alone cannot validate mixin application, world creation, dimensions, remote interaction or the rendered result. Inspect logs and screenshots before interpreting a runtime pass; automated scenes do not cover every feature, GPU or shaderpack. Sodium/Iris and external mods require their own exercised compatibility cases.

## Review requirements

Record the exact commit, workflow URL, passing and failing jobs, and any unexercised features in the PR. Do not label the port complete while a required job fails or a known functional omission remains. See [migration findings](migration-audit-26.2.md) and [rendering design](rendering-26.2.md).

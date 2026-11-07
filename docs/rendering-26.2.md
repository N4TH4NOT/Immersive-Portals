# Rendering decisions

[English](rendering-26.2.md) · [Français](rendering-26.2.fr.md)

The main renderer uses Blaze3D on both Minecraft backends. Iris has a separate OpenGL adapter for its active targets; its internal pipelines are not the portal renderer's general backend.

## Visibility and composition

Portal coverage is tested against parent depth without altering it. GPU reduction returns four bytes; histories are isolated by view and recursion path. Geometry, camera, projection or target changes invalidate reuse. Missing/stale measurements keep the view visible.

Prediction reuses compatible recent results. Without prediction, the current-frame measurement has a bounded wait and falls back to rendering. Per-view snapshots also isolate native/Sodium frustums and terrain invalidation from asynchronous tasks.

| Mode | Composition |
| --- | --- |
| Normal / Iris normal | Texture stencil constrains a fullscreen composition pass. |
| Compatibility / Iris compatibility | Direct portal triangles with explicit parent-plane clipping. |
| Debug / disabled | First remote view fullscreen / no remote world rendering. |
| Iris experimental | Batched visibility prepasses, bounded masks. |

Texture stencils replace unavailable native S8 operations. Switching modes preserves pipelines already extracted for the current frame; resource reload releases contexts and masks.

For coplanar backing walls, coverage and composition share `portalSurfaceDepth`: a reverse-Z offset of 16 representable float steps, bounded to the depth range. A masked composition reads the already offset depth from its coverage texture; it does not apply the offset twice. The adjustment affects raster depth, leaving the geometric portal and collision planes unchanged.

`useDepthClampForPortalRendering` defaults to `true`; disabling it retains native near/far clipping. With it enabled, the vertex shader keeps a neutral clip Z while retaining each vertex's original clip Z for interpolation. The fragment shader reconstructs depth from that interpolated value and reciprocal W, then clamps to the backend's depth range before applying the shared surface offset. This emulates depth clamping across the whole aperture, including corners with negative W, instead of clamping only vertices in front of the eye. Parent-plane clipping still applies. The current near-camera regression and its GitHub result determine whether this shader revision passes the exercised views.

## Terrain updates with Sodium

Each remote view owns a scoped extractor and its terrain meshes. Block changes must invalidate every extractor for the same client world, including neighboring sections where mesh lighting can change. Sodium replaces update entry points even when no Iris shaderpack is active.

[Commit `9a90027b`](https://github.com/N4TH4NOT/Immersive-Portals/commit/9a90027b955914ecdfd1363b489fcc788e5f2cfa) forwards `setBlocksDirty`, `setBlockDirty` and `setSectionDirtyWithNeighbors` updates to each scoped extractor. Block ranges include neighboring blocks before conversion to section coordinates; the existing propagation guard prevents recursive forwarding. This describes the source change. Its effect on an observed stale-mesh or smooth-lighting issue requires the GitHub runtime evidence for the revision containing it.

## Settings and validation

Current settings are defined in [`IPConfig`](../src/main/java/qouteall/imm_ptl/core/platform_specific/IPConfig.java); diagnostics in [`PortalDebugCommands`](../src/main/java/qouteall/imm_ptl/core/commands/PortalDebugCommands.java). Obsolete OpenGL buffer/cache options were removed instead of exposing ineffective controls. Legacy JSON keys are ignored.

The [GitHub runtime guide](port-26.2.md) describes the rendering scenarios and retained evidence. The built-in suite does not establish arbitrary Sodium/Iris or shaderpack compatibility. Cave culling conservatively falls back for incompatible transformed cameras; octree rejection requires the entire volume to be outside the visible region.

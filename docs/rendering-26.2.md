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

`useDepthClampForPortalRendering` defaults to `true`; disabling it retains native near/far clipping. With it enabled, the vertex shader uses neutral clip Z so an aperture crossing the eye plane remains covered after clipping. For planar apertures, [`NativePortalDepth`](../src/main/java/qouteall/imm_ptl/core/render/NativePortalDepth.java) computes the transformed portal-plane equation on the CPU in double precision and converts it to affine window-depth coefficients. Coverage and direct composition receive those coefficients as uniforms and evaluate depth from each fragment's pixel position. This avoids reciprocal-W interpolation cancellation near the eye, including oblique views with corners behind it.

The fragment depth is clamped to the backend's depth range before the shared surface offset is applied. Nonplanar shapes, including box portals, and degenerate transformed planes retain the general reconstruction from interpolated clip Z and reciprocal W. Parent-plane clipping still applies, and neither path moves the geometric aperture. Use the GitHub runtime results for the matching commit to confirm these paths; shader compilation alone does not establish their rendered output.

## Terrain updates with Sodium

Each remote view owns a scoped extractor and its terrain meshes. Block changes must invalidate every extractor for the same client world, including neighboring sections where mesh lighting can change. Sodium replaces update entry points even when no Iris shaderpack is active.

The update hooks forward `setBlocksDirty`, `setBlockDirty` and `setSectionDirtyWithNeighbors` to each scoped extractor. Block ranges include neighboring blocks before conversion to section coordinates; the propagation guard prevents recursive forwarding. GitHub regressions exercise live destination-block changes and multiplayer movement with Sodium, including same-world traversal and restart. Their fixed fixtures do not isolate every ambient-occlusion calculation or establish compatibility with arbitrary mod combinations.

## Settings and validation

Current settings are defined in [`IPConfig`](../src/main/java/qouteall/imm_ptl/core/platform_specific/IPConfig.java); diagnostics in [`PortalDebugCommands`](../src/main/java/qouteall/imm_ptl/core/commands/PortalDebugCommands.java). Obsolete OpenGL buffer/cache options were removed instead of exposing ineffective controls. Legacy JSON keys are ignored.

The [GitHub runtime guide](port-26.2.md) describes the rendering scenarios and retained evidence. The built-in suite does not establish arbitrary Sodium/Iris or shaderpack compatibility. Cave culling conservatively falls back for incompatible transformed cameras; octree rejection requires the entire volume to be outside the visible region.

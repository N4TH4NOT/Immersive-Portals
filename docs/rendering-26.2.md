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

## Settings and validation

Current settings are defined in [`IPConfig`](../src/main/java/qouteall/imm_ptl/core/platform_specific/IPConfig.java); diagnostics in [`PortalDebugCommands`](../src/main/java/qouteall/imm_ptl/core/commands/PortalDebugCommands.java). Obsolete OpenGL buffer/cache options were removed instead of exposing ineffective controls. Legacy JSON keys are ignored.

The [GitHub runtime guide](port-26.2.md) describes the rendering scenarios and retained evidence. The built-in suite does not establish arbitrary Sodium/Iris or shaderpack compatibility. Cave culling conservatively falls back for incompatible transformed cameras; octree rejection requires the entire volume to be outside the visible region.

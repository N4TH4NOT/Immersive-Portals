# Minecraft 26.2 migration findings

[English](migration-audit-26.2.md) · [Français](migration-audit-26.2.fr.md)

This document distinguishes inherited migration work from validation of the standalone Fabric repository. [Provenance](provenance.md) identifies the imported revision; [upstream scope](upstream-1.21-review.md) describes its earlier review.

## Inherited adaptations

| Area | Adaptation |
| --- | --- |
| Build and APIs | Java 25, unremapped Minecraft classes, current Fabric/Loom dependencies, NBT and registry codecs. |
| World lifecycle | Native dimension lifecycle, world creation mixins, remote worlds and chunk-ticket transitions. |
| Teleportation and physics | Current entity references/transitions, collision, native scaling, gravity adapter and interpolation. |
| Rendering | Blaze3D world rendering and portal composition for OpenGL/Vulkan; separate optional Sodium/Iris adapters. |
| Items and generation | Current item definitions, reverse portal generation rules, commands, GUI and example datapack formats. |
| Networking | Redirected native packets and the inherited version-2 client bridge. |

The standalone migration restores automatic registration of the five historical alternate dimensions, saved-world persistence, world-seed selection, dimension-stack creation and templates. The internal DimLib-compatible API replaces the unavailable external dependency. See [alternate dimensions](alternate-dimensions.md) for configuration semantics and required GitHub evidence; the datapack examples alone would not preserve these behaviors.

Legacy Pehkui, Flywheel, Porting Lib and Requiem adapters are not provided as 26.2 integrations. Native scaling is independent of Pehkui. Removing an old adapter does not establish compatibility with an installed replacement.

## Current rendering follow-up

The source now propagates Sodium block and neighboring-section invalidations across scoped portal extractors ([`9a90027b`](https://github.com/N4TH4NOT/Immersive-Portals/commit/9a90027b955914ecdfd1363b489fcc788e5f2cfa)). This targets stale remote terrain after updates, including lighting-related remeshing, without requiring an active shaderpack. The wall-collision correction retains its `1e-7`-block boundary tolerance; coplanar coverage and composition retain their shared 16-float-step reverse-Z offset. Neither changes the geometric portal plane. The depth-clamp shaders now reconstruct and clamp fragment depth across the full aperture, including vertices behind the eye. Destination collision contact is refreshed at the final entity position without relying on a potentially stale source entity-section index; contact can remain valid across a frame-time crossing based on either previous or current eye position.

Validation of the latest combination is pending its GitHub Actions run. Slow approach, stopping at the portal, reversing input and crossing with the backing wall intact are separate movement acceptance cases; the collision or rendering fixes alone do not establish them. See [rendering details](rendering-26.2.md) and [troubleshooting](troubleshooting.md) for the implementation scope.

## Evidence and remaining scope

Earlier source-fork documentation records manual runtime experiments, but those experiments are not rerun evidence for this repository. The imported GitHub workflow establishes compilation/unit-test success only. The destination [validation workflow](port-26.2.md) adds explicit gameplay checks and retains logs for review.

A complete migration requires passing destination compilation/tests, working dimensions and world persistence, portal generation and traversal, collision/scale/rotation, remote interaction, recursion, GUI rendering, resource reload and both supported native backends. Optional integrations need separate compatibility evidence. A finite smoke suite does not establish every mod combination or rendering device.

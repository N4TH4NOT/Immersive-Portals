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

The imported implementation removed automatic registration of the five historical alternate dimensions. Restoring their lifecycle and configuration is part of the standalone migration; datapack examples alone do not preserve that behavior. Check the current implementation and destination CI for its status.

Legacy external DimLib, Pehkui, Flywheel, Porting Lib and Requiem adapters are not provided as 26.2 integrations. Native scaling is independent of Pehkui. Removing an old adapter does not establish compatibility with an installed replacement.

## Evidence and remaining scope

Earlier source-fork documentation records manual runtime experiments, but those experiments are not rerun evidence for this repository. The imported GitHub workflow establishes compilation/unit-test success only. The destination [validation workflow](port-26.2.md) adds explicit gameplay checks and retains logs for review.

A complete migration requires passing destination compilation/tests, working dimensions and world persistence, portal generation and traversal, collision/scale/rotation, remote interaction, recursion, GUI rendering, resource reload and both supported native backends. Optional integrations need separate compatibility evidence. A finite smoke suite does not establish every mod combination or rendering device.

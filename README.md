# Immersive Portals — Minecraft 26.2 / Fabric

[English](README.md) · [Français](README.fr.md)

Fabric port of [Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod): see through portals and travel between worlds seamlessly. The renderer targets Minecraft's native OpenGL and Vulkan backends. This repository distributes the Fabric mod; the Minestom server SDK remains in its [own repository](https://github.com/N4TH4NOT/immersive-portals-minestom).

## Installation

Use Minecraft Java **26.2**, Java **25**, Fabric Loader and Fabric API. Exact dependency versions are pinned in [gradle.properties](gradle.properties).

Download a successful revision's mod JAR from [GitHub Actions](https://github.com/N4TH4NOT/Immersive-Portals/actions), then place it in the client's and Fabric server's `mods` directory with Fabric API. Cloth Config and the gravity adapter/API are bundled. Directional player gravity additionally requires [Gravity Changer](https://github.com/N4TH4NOT/gravity-changer#fabric-installation-and-use). Development artifacts are not stable releases.

Select Vulkan through Minecraft's `--graphicsBackend vulkan` option. Iris uses the OpenGL adapter; compatibility with an arbitrary shaderpack or GPU requires separate verification.

## Development and status

All compilation, automated tests and runtime scenarios run **exclusively on GitHub Actions**. See the [validation guide](docs/port-26.2.md) for required checks, retained evidence and limits. A successful build does not establish complete gameplay compatibility.

- [Source provenance and scope](docs/provenance.md), [upstream review](docs/upstream-1.21-review.md) and [migration findings](docs/migration-audit-26.2.md).
- [Rendering design](docs/rendering-26.2.md), [network compatibility](docs/network-audit-26.2.md) and [gravity integration](docs/gravity-repository.md).
- [Built-in alternate dimensions](docs/alternate-dimensions.md) and [dimension API/commands](docs/dimension-library.md).
- [Optional generation datapack](examples/custom_portal_gen/README.md).

Dimension management, including the DimLib API and templates, is adapted internally without an external DimLib dependency. Legacy Pehkui, Flywheel, Porting Lib and Requiem adapters are not supplied as 26.2 integrations. Native entity scaling does not require Pehkui. Remaining differences and the evidence required before declaring the migration complete are documented in the [migration findings](docs/migration-audit-26.2.md).

Original work by qouteall and contributors, under [Apache-2.0](LICENSE). This port preserves their source history and reuses the 26.2 adaptations from N4TH4NOT's [Fabric/Minestom fork](https://github.com/N4TH4NOT/immersive-portals-minestom/tree/67bf29333b82b0d6c594c65d84256c881f0182b4). The [upstream wiki](https://qouteall.fun/immptl/wiki/) describes earlier engine versions.

# Embedded dimension library

[English](dimension-library.md) · [Français](dimension-library.fr.md)

The Fabric mod embeds the Apache-2.0 DimLib API design and dimension commands. The adaptation is based on [iPortalTeam/DimLib, branch 1.21.3](https://github.com/iPortalTeam/DimLib/tree/1.21.3); its license and attribution are retained in [licenses](../licenses). No separate DimLib JAR is required.

Operators can use `/dims list`, `/dims add_dimension "example:new_world" void`, `/dims clone_dimension minecraft:overworld "example:copy"`, `/dims view_dim_config example:new_world` and `/dims remove_dimension example:new_world`. Additional templates are registered by Immersive Portals, including skyland, bright_skyland, chaos and bright_void. Cloning copies the generator definition, rather than blocks, entities or a mutable generator instance.

For integrations, `qouteall.dimlib.api.DimensionAPI` exposes creation, creation-if-missing, deferred removal, dimension existence/aliveness, template registration, lifecycle events and synchronized client dimension/type maps. Minecraft 26.2 uses `Identifier` in place of the old `ResourceLocation` API type. Mutations must run on the server thread, after the native base worlds exist. Dimension types must already be registered.

Runtime additions update the native dimension registry and `WorldGenSettings` saved data. Removal runs at the end of a tick, not during world iteration: players are evacuated to overworld spawn, portal tracking is notified, chunk/entity saves are flushed, the world is closed and its generator definition is removed. World directories are preserved. Definitions supplied by a datapack, or the default alternate-dimension configuration, may add the dimension again on a later load.

The embedded library shares Immersive Portals' dimension integer mapping, client type synchronization and cleanup events. It also updates vanilla client dimension suggestions. The `SERVER_DIMENSIONS_LOAD_EVENT` runs after native base-world construction and before applying dimension stacks; listeners can create additional worlds there. This differs from the earlier library's pre-construction hook.

Warning suppression remains available through `suppressExperimentalWarning()` and `suppressExperimentalWarningForNamespace(...)`; the mod marks its own namespace stable. It does not globally suppress other mods' experimental settings by default. The former separate MidnightConfig settings screen is not bundled.

Compilation and runtime validation run exclusively on GitHub Actions. The dynamic-dimension scenario must establish connected-client synchronization, removal cleanup/evacuation and saved-world reload behavior for the exact revision before those results are claimed. See the [validation guide](port-26.2.md).

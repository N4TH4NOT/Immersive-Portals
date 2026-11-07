# Alternate dimensions

[English](alternate-dimensions.md) · [Français](alternate-dimensions.fr.md)

The Fabric 26.2 port provides `immersive_portals:skyland`, `bright_skyland`, `chaos`, `void` and `bright_void` through Minecraft's native dimension registry. No DimLib dependency or additional datapack is required. Bright variants use the existing bright surface dimension type; alternate-dimension weather still follows the overworld.

`enableAlternateDimensions` defaults to `true`. At world loading, the mod adds missing definitions before the server registry layers are assembled. Saved definitions and datapacks take precedence, so existing terrain generators are preserved. Set the option to `false` before starting a world to stop adding defaults. This does not remove dimensions already saved in that world, their chunks, or definitions supplied by a datapack.

Native skyland defaults use the world's seed and preserve that choice when the generator is saved. Datapacks can still supply a fixed `seed`; `use_world_seed: true` instead selects the server's seed at generator initialization. Chaos terrain retains the upstream intentionally nondeterministic regional generator. The two void dimensions use the empty flat generator.

All five dimensions are available to dimension stacks from world creation onward. Definitions must be prepared before loading the world; this port does not expose DimLib's runtime add/remove API. Restart after adding a dimension datapack or changing the configuration.

GitHub's unit checks cover disabled registration, saved/datapack precedence, missing void variants, and registry metadata preservation. The Fabric runtime scenario generates chunks in all five dimensions and reloads their generator codecs, with the marker `PORTAL_SMOKE_ALTERNATE_DIMENSIONS_OK`. See the [validation guide](port-26.2.md) for the scenarios; compilation alone does not establish in-game behavior.

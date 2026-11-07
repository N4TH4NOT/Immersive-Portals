package qouteall.imm_ptl.peripheral.alternate_dimension;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import com.mojang.serialization.Lifecycle;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackAPI;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.tags.TagKey;

public class AlternateDimensions {
    
    public static final ResourceKey<DimensionType> SURFACE_TYPE = ResourceKey.create(
        Registries.DIMENSION_TYPE,
        McHelper.newIdentifier("immersive_portals:surface_type")
    );
    
    public static final ResourceKey<DimensionType> SURFACE_TYPE_BRIGHT = ResourceKey.create(
        Registries.DIMENSION_TYPE,
        McHelper.newIdentifier("immersive_portals:surface_type_bright")
    );
    
    public static final ResourceKey<Level> SKYLAND = ResourceKey.create(
        Registries.DIMENSION,
        McHelper.newIdentifier("immersive_portals:skyland")
    );
    
    public static final ResourceKey<Level> BRIGHT_SKYLAND = ResourceKey.create(
        Registries.DIMENSION,
        McHelper.newIdentifier("immersive_portals:bright_skyland")
    );
    
    public static final ResourceKey<Level> CHAOS = ResourceKey.create(
        Registries.DIMENSION,
        McHelper.newIdentifier("immersive_portals:chaos")
    );
    
    public static final ResourceKey<Level> VOID = ResourceKey.create(
        Registries.DIMENSION,
        McHelper.newIdentifier("immersive_portals:void")
    );
    
    public static final ResourceKey<Level> BRIGHT_VOID = ResourceKey.create(
        Registries.DIMENSION,
        McHelper.newIdentifier("immersive_portals:bright_void")
    );

    public static final List<ResourceKey<Level>> BUILT_IN_DIMENSIONS =
        List.of(SKYLAND, BRIGHT_SKYLAND, CHAOS, VOID, BRIGHT_VOID);
    
    public static void init() {
        DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT.register(
            (registries, options) -> IPConfig.getConfig().enableAlternateDimensions
                ? BUILT_IN_DIMENSIONS : List.of()
        );
        ServerTickEvents.END_SERVER_TICK.register(AlternateDimensions::tick);
    }

    /** Extend the final dimension registry before it enters the native server registry layers. */
    public static <D> WorldLoader.WorldDataSupplier<D> withAlternateDimensions(
        WorldLoader.WorldDataSupplier<D> supplier
    ) {
        return context -> {
            WorldLoader.DataLoadOutput<D> output = supplier.get(context);
            RegistryAccess.Frozen dimensions = addMissingDimensions(
                context.datapackWorldgen(), output.finalDimensions(),
                IPConfig.getConfig().enableAlternateDimensions
            );
            return dimensions == output.finalDimensions() ? output
                : new WorldLoader.DataLoadOutput<>(output.cookie(), dimensions);
        };
    }

    /** Existing saved or datapack definitions always take precedence over generated defaults. */
    public static RegistryAccess.Frozen addMissingDimensions(
        HolderLookup.Provider worldgen, RegistryAccess.Frozen dimensions, boolean enabled
    ) {
        if (!enabled) return dimensions;
        Registry<LevelStem> existing = dimensions.lookupOrThrow(Registries.LEVEL_STEM);
        if (BUILT_IN_DIMENSIONS.stream().allMatch(key ->
            existing.containsKey(ResourceKey.create(Registries.LEVEL_STEM, key.identifier()))
        )) return dimensions;

        MappedRegistry<LevelStem> replacement =
            new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.experimental());
        existing.listElements().forEach(holder -> replacement.register(
            holder.key(), holder.value(), existing.registrationInfo(holder.key())
                .orElse(RegistrationInfo.BUILT_IN)
        ));
        for (ResourceKey<Level> key : BUILT_IN_DIMENSIONS) {
            ResourceKey<LevelStem> stemKey =
                ResourceKey.create(Registries.LEVEL_STEM, key.identifier());
            if (!replacement.containsKey(stemKey)) {
                replacement.register(stemKey, createDefaultStem(worldgen, key),
                    new RegistrationInfo(Optional.empty(), Lifecycle.stable()));
            }
        }
        Map<TagKey<LevelStem>, List<Holder<LevelStem>>> tags = new HashMap<>();
        existing.listTags().forEach(tag -> tags.put(tag.key(), tag.stream()
            .map(holder -> (Holder<LevelStem>) replacement.getOrThrow(holder.unwrapKey().orElseThrow()))
            .toList()));
        replacement.bindTags(tags);
        replacement.freeze();

        List<Registry<?>> registries = new ArrayList<>();
        dimensions.registries().forEach(entry -> registries.add(
            entry.key().equals(Registries.LEVEL_STEM) ? replacement : entry.value()
        ));
        return new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
    }

    private static LevelStem createDefaultStem(
        HolderLookup.Provider worldgen, ResourceKey<Level> key
    ) {
        ResourceKey<DimensionType> type =
            key == BRIGHT_SKYLAND || key == BRIGHT_VOID ? SURFACE_TYPE_BRIGHT : SURFACE_TYPE;
        ChunkGenerator generator;
        if (key == SKYLAND || key == BRIGHT_SKYLAND) {
            generator = NormalSkylandGenerator.create(
                worldgen.lookupOrThrow(Registries.BIOME),
                worldgen.lookupOrThrow(Registries.DENSITY_FUNCTION),
                worldgen.lookupOrThrow(Registries.NOISE),
                worldgen.lookupOrThrow(Registries.NOISE_SETTINGS),
                worldgen.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
                0L, true
            );
        } else if (key == CHAOS) {
            generator = ErrorTerrainGenerator.create(
                worldgen.lookupOrThrow(Registries.BIOME),
                worldgen.lookupOrThrow(Registries.NOISE_SETTINGS)
            );
        } else {
            generator = createVoidGenerator(worldgen);
        }
        return new LevelStem(worldgen.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(type), generator);
    }

    public static boolean isAlternateDimension(Level world) {
        ResourceKey<DimensionType> dimensionTypeId = world.dimensionTypeRegistration().unwrapKey().orElseThrow();
        return dimensionTypeId == SURFACE_TYPE
            || dimensionTypeId == SURFACE_TYPE_BRIGHT;
    }
    
    public static ChunkGenerator createSkylandGenerator(RegistryAccess rm, long seed) {
        return NormalSkylandGenerator.create(
            rm.lookupOrThrow(Registries.BIOME),
            rm.lookupOrThrow(Registries.DENSITY_FUNCTION),
            rm.lookupOrThrow(Registries.NOISE),
            rm.lookupOrThrow(Registries.NOISE_SETTINGS),
            rm.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
            seed
        );
    }
    
    public static ChunkGenerator createErrorTerrainGenerator(long seed, RegistryAccess rm) {
        return ErrorTerrainGenerator.create(
            rm.lookupOrThrow(Registries.BIOME),
            rm.lookupOrThrow(Registries.NOISE_SETTINGS)
        );
    }
    
    public static ChunkGenerator createVoidGenerator(RegistryAccess rm) {
        return createVoidGenerator((HolderLookup.Provider) rm);
    }

    public static ChunkGenerator createVoidGenerator(HolderLookup.Provider rm) {
        var biomeRegistry = rm.lookupOrThrow(Registries.BIOME);
        
        Holder.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(Biomes.PLAINS);
        
        FlatLevelGeneratorSettings flatChunkGeneratorConfig =
            new FlatLevelGeneratorSettings(
                Optional.of(HolderSet.direct()),
                plainsHolder,
                List.of()
            );
        flatChunkGeneratorConfig.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        flatChunkGeneratorConfig.updateLayers();
        
        return new FlatLevelSource(flatChunkGeneratorConfig);
    }
    
    private static void tick(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            if (isAlternateDimension(world)) {
                syncWeatherFromOverworld(
                    world, McHelper.getOverWorldOnServer()
                );
            }
        }
    }
    
    private static void syncWeatherFromOverworld(
        ServerLevel world, ServerLevel overworld
    ) {
        ((IEWorld) world).portal_setWeather(
            overworld.getRainLevel(1), overworld.getRainLevel(1),
            overworld.getThunderLevel(1), overworld.getThunderLevel(1)
        );
    }
}

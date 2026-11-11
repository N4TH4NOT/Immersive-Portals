package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AlternateDimensionsTest {
    private static HolderLookup.Provider worldgen;
    private static Registry<DimensionType> types;

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var vanilla = VanillaRegistries.createLookup();
        var registry = new MappedRegistry<DimensionType>(Registries.DIMENSION_TYPE, Lifecycle.stable());
        registry.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD,
            vanilla.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD).value(),
            RegistrationInfo.BUILT_IN);
        for (var type : List.of(AlternateDimensions.SURFACE_TYPE, AlternateDimensions.SURFACE_TYPE_BRIGHT)) {
            String path = "/data/immersive_portals/dimension_type/" + type.identifier().getPath() + ".json";
            try (var reader = new InputStreamReader(
                    java.util.Objects.requireNonNull(AlternateDimensionsTest.class.getResourceAsStream(path)),
                    StandardCharsets.UTF_8)) {
                var value = DimensionType.DIRECT_CODEC.parse(
                    RegistryOps.create(JsonOps.INSTANCE, vanilla), JsonParser.parseReader(reader)
                ).getOrThrow();
                registry.register(type, value, RegistrationInfo.BUILT_IN);
            }
        }
        types = registry.freeze();
        worldgen = HolderLookup.Provider.create(Stream.concat(
            vanilla.listRegistries().filter(lookup -> !lookup.key().equals(Registries.DIMENSION_TYPE)),
            Stream.of(types)
        ));
    }

    @Test
    void disabledRegistrationLeavesTheSavedRegistryUntouched() {
        var dimensions = access(stems(List.of(AlternateDimensions.SKYLAND)));
        assertSame(dimensions, AlternateDimensions.addMissingDimensions(worldgen, dimensions, false));
        assertEquals(1, dimensions.lookupOrThrow(Registries.LEVEL_STEM).size());
    }

    @Test
    void completeSavedDefinitionsRemainAuthoritative() {
        var dimensions = access(stems(AlternateDimensions.BUILT_IN_DIMENSIONS));
        assertSame(dimensions, AlternateDimensions.addMissingDimensions(worldgen, dimensions, true));
    }

    @Test
    void worldGenerationSettingsSaveTheAugmentedRegistryAndKeepTheOriginalSeed() {
        var original = access(stems(List.of(Level.OVERWORLD, AlternateDimensions.SKYLAND,
            AlternateDimensions.BRIGHT_SKYLAND, AlternateDimensions.CHAOS)));
        var options = new net.minecraft.world.level.levelgen.WorldOptions(987654321L, false, false);
        var cookie = new net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings(
            null, new net.minecraft.world.level.levelgen.WorldGenSettings(options,
                new net.minecraft.world.level.levelgen.WorldDimensions(
                    original.lookupOrThrow(Registries.LEVEL_STEM)))
        );
        var augmented = AlternateDimensions.addMissingDimensions(worldgen, original, true);
        var saved = AlternateDimensions.withSavedDimensions(cookie, augmented);
        assertSame(options, saved.genSettings().options());
        assertEquals(6, saved.genSettings().dimensions().dimensions().size());
        for (var key : AlternateDimensions.BUILT_IN_DIMENSIONS)
            assertSame(augmented.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(stemKey(key)).value(),
                saved.genSettings().dimensions().get(stemKey(key)).orElseThrow());
        assertEquals(4, cookie.genSettings().dimensions().dimensions().size());
        Object otherCookie = new Object();
        assertSame(otherCookie, AlternateDimensions.withSavedDimensions(otherCookie, augmented));
    }

    @Test
    void fillsMissingVoidVariantsWithoutChangingExistingDefinitionsOrRegistryMetadata() {
        var original = stems(List.of(AlternateDimensions.SKYLAND,
            AlternateDimensions.BRIGHT_SKYLAND, AlternateDimensions.CHAOS));
        var marker = TagKey.create(Registries.LEVEL_STEM, Identifier.parse("test:preserved"));
        original.bindTags(Map.of(marker, List.of(original.getOrThrow(stemKey(AlternateDimensions.SKYLAND)))));
        var dimensions = access(original);
        var result = AlternateDimensions.addMissingDimensions(worldgen, dimensions, true);
        var merged = result.lookupOrThrow(Registries.LEVEL_STEM);
        assertEquals(5, merged.size());
        for (var holder : original.listElements().toList()) {
            assertSame(holder.value(), merged.getOrThrow(holder.key()).value());
            assertEquals(original.getId(holder.value()), merged.getId(holder.value()));
            assertEquals(original.registrationInfo(holder.key()), merged.registrationInfo(holder.key()));
        }
        assertSame(types, result.lookupOrThrow(Registries.DIMENSION_TYPE));
        assertEquals(stemKey(AlternateDimensions.SKYLAND),
            merged.get(marker).orElseThrow().get(0).unwrapKey().orElseThrow());
        for (var key : List.of(AlternateDimensions.VOID, AlternateDimensions.BRIGHT_VOID)) {
            var stem = merged.getOrThrow(stemKey(key)).value();
            var generator = assertInstanceOf(FlatLevelSource.class, stem.generator());
            assertTrue(generator.settings().getLayers().stream().allMatch(state -> state.is(Blocks.AIR)));
            assertEquals(key == AlternateDimensions.BRIGHT_VOID
                    ? AlternateDimensions.SURFACE_TYPE_BRIGHT
                    : net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD,
                stem.type().unwrapKey().orElseThrow());
        }
        assertEquals(3, original.size());
        assertSame(result, AlternateDimensions.addMissingDimensions(worldgen, result, true));
    }

    private static MappedRegistry<LevelStem> stems(List<ResourceKey<Level>> keys) {
        var registry = new MappedRegistry<LevelStem>(Registries.LEVEL_STEM, Lifecycle.stable());
        for (var key : keys) registry.register(stemKey(key), new LevelStem(
            types.getOrThrow(AlternateDimensions.SURFACE_TYPE), AlternateDimensions.createVoidGenerator(worldgen)
        ), new RegistrationInfo(Optional.empty(), Lifecycle.experimental()));
        return registry;
    }

    private static RegistryAccess.Frozen access(MappedRegistry<LevelStem> stems) {
        stems.freeze();
        return new RegistryAccess.ImmutableRegistryAccess(List.of(stems, types)).freeze();
    }

    private static ResourceKey<LevelStem> stemKey(ResourceKey<Level> key) {
        return ResourceKey.create(Registries.LEVEL_STEM, key.identifier());
    }
}

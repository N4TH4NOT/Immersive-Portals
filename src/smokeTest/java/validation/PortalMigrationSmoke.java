package validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions;
import qouteall.imm_ptl.peripheral.alternate_dimension.NormalSkylandGenerator;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Exercises the published data against the game's codecs with Fabric mixins active. */
final class PortalMigrationSmoke {
    static void validateAlternateDimensions(net.minecraft.server.MinecraftServer server) {
        boolean reload = Boolean.getBoolean("portalSmoke.dimensionReload");
        if (reload && IPConfig.getConfig().enableAlternateDimensions)
            throw new AssertionError("Dimension reload must run with automatic dimension creation disabled");
        var dimensions = server.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.LEVEL_STEM);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var snapshotPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("portal-smoke-dimensions.json");
        JsonObject previous = null;
        if (reload) {
            try (var reader = Files.newBufferedReader(snapshotPath, StandardCharsets.UTF_8)) {
                previous = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (java.io.IOException exception) {
                throw new AssertionError("Missing first-run dimension snapshot", exception);
            }
            if (previous.get("seed").getAsLong() != server.getWorldGenSettings().options().seed())
                throw new AssertionError("World seed changed on dimension reload");
        }
        var snapshot = new JsonObject();
        snapshot.addProperty("seed", server.getWorldGenSettings().options().seed());
        var generators = new JsonObject();
        snapshot.add("generators", generators);
        BlockPos marker = new BlockPos(8, 200, 8);
        for (var key : AlternateDimensions.BUILT_IN_DIMENSIONS) {
            var world = server.getLevel(key);
            if (world == null || !dimensions.containsKey(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.LEVEL_STEM, key.identifier())))
                throw new AssertionError("Native alternate dimension missing: " + key.identifier());
            // Actual chunk generation also exercises mixin invokers and native random-state setup.
            world.getChunk(0, 0);
            var generator = world.getChunkSource().getGenerator();
            var encoded = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow();
            var restored = ChunkGenerator.CODEC.parse(ops, encoded).getOrThrow();
            var reencoded = ChunkGenerator.CODEC.encodeStart(ops, restored).getOrThrow();
            if (!encoded.equals(reencoded))
                throw new AssertionError("Alternate generator changed on codec reload: " + key.identifier());
            if (key == AlternateDimensions.BRIGHT_SKYLAND
                    && !encoded.getAsJsonObject().get("use_world_seed").getAsBoolean())
                throw new AssertionError("Native skyland does not use the world's seed");
            if (generator instanceof NormalSkylandGenerator skyland
                    && encoded.getAsJsonObject().has("use_world_seed")
                    && encoded.getAsJsonObject().get("use_world_seed").getAsBoolean())
                validateNativeSkylandSeed(server, skyland);
            if (server.getWorldGenSettings().dimensions().get(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.LEVEL_STEM, key.identifier())).isEmpty())
                throw new AssertionError("Alternate dimension absent from saved worldgen settings: " + key.identifier());
            if (reload) {
                if (!encoded.equals(previous.getAsJsonObject("generators").get(key.identifier().toString())))
                    throw new AssertionError("Saved dimension generator changed after restart: " + key.identifier());
                if (!world.getBlockState(marker).is(Blocks.BARRIER))
                    throw new AssertionError("Alternate dimension chunk was not preserved: " + key.identifier());
            } else {
                world.setBlock(marker, Blocks.BARRIER.defaultBlockState(), 3);
            }
            generators.add(key.identifier().toString(), encoded);
        }
        if (!reload) {
            try {
                Files.writeString(snapshotPath, snapshot.toString(), StandardCharsets.UTF_8);
            } catch (java.io.IOException exception) {
                throw new AssertionError("Could not write first-run dimension snapshot", exception);
            }
        } else System.out.println("PORTAL_SMOKE_DIMENSION_RELOAD_OK");
        System.out.println("PORTAL_SMOKE_ALTERNATE_DIMENSIONS_OK");
    }

    private static void validateNativeSkylandSeed(
            net.minecraft.server.MinecraftServer server, NormalSkylandGenerator generator) {
        try {
            var stateField = NormalSkylandGenerator.class.getDeclaredField("delegatedRandomState");
            var delegateField = NormalSkylandGenerator.class.getDeclaredField("delegate");
            stateField.setAccessible(true);
            delegateField.setAccessible(true);
            RandomState actual = (RandomState) stateField.get(generator);
            var delegate = (NoiseBasedChunkGenerator) delegateField.get(generator);
            long seed = server.getWorldGenSettings().options().seed();
            var noiseParameters = server.registryAccess().lookupOrThrow(
                    net.minecraft.core.registries.Registries.NOISE);
            var expected = RandomState.create(delegate.generatorSettings().value(), noiseParameters, seed);
            var different = RandomState.create(delegate.generatorSettings().value(), noiseParameters,
                    seed ^ 0x5DEECE66DL);
            boolean distinguishesSeeds = false;
            for (int sample = 0; sample < 8; sample++) {
                var point = new DensityFunction.SinglePointContext(sample * 117 + 23, 64, sample * -73 + 41);
                for (var functions : List.of(
                        List.of(actual.router().temperature(), expected.router().temperature(), different.router().temperature()),
                        List.of(actual.router().vegetation(), expected.router().vegetation(), different.router().vegetation()),
                        List.of(actual.router().finalDensity(), expected.router().finalDensity(), different.router().finalDensity()))) {
                    double found = functions.get(0).compute(point), wanted = functions.get(1).compute(point);
                    if (Double.compare(found, wanted) != 0)
                        throw new AssertionError("Skyland's delegated noise does not use the native world seed");
                    distinguishesSeeds |= Double.compare(wanted, functions.get(2).compute(point)) != 0;
                }
            }
            if (!distinguishesSeeds)
                throw new AssertionError("Skyland seed verification sampled no seed-dependent noise");
            System.out.println("PORTAL_SMOKE_SKYLAND_WORLD_SEED_OK");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect skyland's delegated noise", exception);
        }
    }

    static void validate() {
        var ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
        var pack = read("/migration-example/pack.mcmeta").getAsJsonObject().get("pack");
        PackMetadataSection.SERVER_TYPE.codec().parse(ops, pack).getOrThrow();
        for (String name : List.of("surface_type", "surface_type_bright")) {
            DimensionType.DIRECT_CODEC.parse(ops,
                    read("/data/immersive_portals/dimension_type/" + name + ".json")).getOrThrow();
        }
        for (String name : List.of("skyland", "chaos", "void")) {
            var dimension = read("/migration-example/data/immersive_portals/dimension/" + name + ".json");
            ChunkGenerator.CODEC.parse(ops, dimension.getAsJsonObject().get("generator")).getOrThrow();
        }
        for (String name : List.of("aether_like", "convert_vanilla_nether_portal",
                "lapis_redstone_portal", "portal_helper_like", "special_nether_portal",
                "twilight_forest_like", "wood_portal")) {
            var rule = read("/migration-example/data/i_am_namespace/immersive_portals/custom_portal_generation/"
                    + name + ".json");
            validateBlockTags(rule);
            CustomPortalGeneration.CODEC.parse(ops, rule).getOrThrow();
        }
        System.out.println("PORTAL_SMOKE_MIGRATION_RESOURCES_OK");
    }

    private static void validateBlockTags(JsonElement json) {
        if (json.isJsonObject()) json.getAsJsonObject().entrySet().forEach(e -> validateBlockTags(e.getValue()));
        else if (json.isJsonArray()) json.getAsJsonArray().forEach(PortalMigrationSmoke::validateBlockTags);
        else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()
                && json.getAsString().startsWith("#")) {
            var id = net.minecraft.resources.Identifier.parse(json.getAsString().substring(1));
            read("/data/" + id.getNamespace() + "/tags/block/" + id.getPath() + ".json");
        }
    }

    private static JsonElement read(String path) {
        var stream = PortalMigrationSmoke.class.getResourceAsStream(path);
        if (stream == null) throw new AssertionError("Missing migration resource " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (java.io.IOException exception) {
            throw new AssertionError(path, exception);
        }
    }
}

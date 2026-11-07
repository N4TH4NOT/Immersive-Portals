package validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Exercises the published data against the game's codecs with Fabric mixins active. */
final class PortalMigrationSmoke {
    static void validateAlternateDimensions(net.minecraft.server.MinecraftServer server) {
        var dimensions = server.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.LEVEL_STEM);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        for (var key : qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions.BUILT_IN_DIMENSIONS) {
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
            if (key == qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions.BRIGHT_SKYLAND
                    && !encoded.getAsJsonObject().get("use_world_seed").getAsBoolean())
                throw new AssertionError("Native skyland does not use the world's seed");
        }
        System.out.println("PORTAL_SMOKE_ALTERNATE_DIMENSIONS_OK");
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

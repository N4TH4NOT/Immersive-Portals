package qouteall.dimlib;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.slf4j.Logger;
import qouteall.dimlib.mixin.DimensionServerAccess;
import qouteall.dimlib.mixin.DimensionSettingsAccess;
import qouteall.q_misc_util.dimension.DimensionLifecycle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.LockSupport;

/** Runtime creation/removal using native 26.2 world-generation saved data. */
public final class DynamicDimensionsImpl {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Set<ServerLevel>> PENDING_REMOVALS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    public static boolean isRemovingDimension;
    private static final ThreadLocal<ServerLevel> REMOVING = new ThreadLocal<>();

    private DynamicDimensionsImpl() {}

    public static void addDimensionDynamically(MinecraftServer server, Identifier id, LevelStem stem) {
        requireServerThread(server);
        if (!server.isRunning()) throw new IllegalStateException("The server is not running");
        var key = ResourceKey.create(Registries.DIMENSION, id);
        if (server.getLevel(key) != null || DimensionImpl.getDimensionRegistry(server).containsKey(id))
            throw new IllegalArgumentException("Dimension already exists: " + id);
        var overworld = server.overworld();
        if (overworld == null) throw new IllegalStateException("The overworld has not been initialized");
        var typeKey = stem.type().unwrapKey().orElseThrow(
            () -> new IllegalArgumentException("Dimension type must be registered before adding a dimension"));
        if (!server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).containsKey(typeKey))
            throw new IllegalArgumentException("Unknown dimension type: " + typeKey.identifier());

        var access = (DimensionServerAccess) server;
        long seed = server.getWorldGenSettings().options().seed();
        var registry = replacementRegistry(server, id, stem);
        ServerLevel world = new ServerLevel(server, access.dimlib_executor(), access.dimlib_storage(),
            new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()),
            key, stem, false, BiomeManager.obfuscateSeed(seed), List.of(), false);
        world.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(world);
        var levels = new LinkedHashMap<>(access.dimlib_levels());
        levels.put(key, world);
        access.dimlib_levels(levels);
        replaceRegistryAndSavedSettings(server, registry);
        DimensionLifecycle.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().accept(server, Set.copyOf(server.levelKeys()));
        ServerLevelEvents.LOAD.invoker().onLevelLoad(server, world);
        LOGGER.info("Added dimension {}", id);
    }

    /** Clone codecs rather than sharing a mutable generator instance across worlds. */
    public static LevelStem cloneLevelStem(MinecraftServer server, LevelStem source) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        return LevelStem.CODEC.parse(ops, LevelStem.CODEC.encodeStart(ops, source).getOrThrow()).getOrThrow();
    }

    public static void removeDimensionDynamically(ServerLevel world) {
        var server = world.getServer();
        requireServerThread(server);
        if (!server.isRunning()) throw new IllegalStateException("The server is not running");
        var key = world.dimension();
        if (key == Level.OVERWORLD || key == Level.NETHER || key == Level.END)
            throw new IllegalArgumentException("Cannot remove a vanilla dimension: " + key.identifier());
        if (server.getLevel(key) != world) throw new IllegalArgumentException("Dimension is not loaded: " + key.identifier());
        PENDING_REMOVALS.computeIfAbsent(server, ignored -> new LinkedHashSet<>()).add(world);
    }

    public static void clearServer(MinecraftServer server) { PENDING_REMOVALS.remove(server); }

    public static void processRemovals(MinecraftServer server) {
        var worlds = PENDING_REMOVALS.remove(server);
        if (worlds == null) return;
        for (var world : worlds) {
            if (server.getLevel(world.dimension()) != world) continue;
            removeNow(world);
        }
    }

    static boolean isRemoving(ServerLevel world) { return REMOVING.get() == world; }

    private static void removeNow(ServerLevel world) {
        var server = world.getServer();
        var key = world.dimension();
        isRemovingDimension = true;
        REMOVING.set(world);
        try {
            // The existing lifecycle bridge invokes the DimLib pre-remove API from this event.
            ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, world);
            var overworld = server.overworld();
            var spawn = overworld.getRespawnData();
            for (var player : List.copyOf(world.players())) {
                if (!player.teleportTo(overworld, spawn.pos().getX() + .5, spawn.pos().getY(),
                        spawn.pos().getZ() + .5, Set.of(), spawn.yaw(), spawn.pitch(), true))
                    throw new IllegalStateException("Could not evacuate player " + player.getName().getString());
                player.sendSystemMessage(Component.literal("Teleported to spawn because dimension "
                    + key.identifier() + " was removed"));
            }
            var chunks = world.getChunkSource();
            chunks.deactivateTicketsOnClosing();
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (chunks.chunkMap.hasWork() && System.nanoTime() < deadline) {
                chunks.tick(() -> true, false);
                if (!chunks.pollTask() && !server.pollTask()) LockSupport.parkNanos(1_000_000L);
            }
            // Flush the removed world's chunks and entities before closing it. No directory is deleted.
            world.save(null, true, false);
            try { world.close(); }
            catch (IOException error) { LOGGER.error("Error closing removed dimension {}", key.identifier(), error); }
            var access = (DimensionServerAccess) server;
            var levels = new LinkedHashMap<>(access.dimlib_levels());
            levels.remove(key);
            access.dimlib_levels(levels);
            replaceRegistryAndSavedSettings(server, replacementRegistry(server, key.identifier(), null));
            DimensionLifecycle.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().accept(server, Set.copyOf(server.levelKeys()));
            LOGGER.info("Removed dimension {}; its world files were preserved", key.identifier());
        } finally {
            REMOVING.remove();
            isRemovingDimension = false;
        }
    }

    /** Preserve surviving values, registration metadata and tags in a fresh frozen registry. */
    private static Registry<LevelStem> replacementRegistry(MinecraftServer server, Identifier id, LevelStem added) {
        Registry<LevelStem> original = DimensionImpl.getDimensionRegistry(server);
        var replacement = new MappedRegistry<LevelStem>(Registries.LEVEL_STEM, original.registryLifecycle());
        original.listElements().forEach(holder -> {
            if (!holder.key().identifier().equals(id)) replacement.register(holder.key(), holder.value(),
                original.registrationInfo(holder.key()).orElse(RegistrationInfo.BUILT_IN));
        });
        if (added != null) replacement.register(ResourceKey.create(Registries.LEVEL_STEM, id), added,
            new RegistrationInfo(Optional.empty(), DimensionImpl.isStableNamespace(id.getNamespace())
                ? Lifecycle.stable() : Lifecycle.experimental()));
        Map<TagKey<LevelStem>, List<Holder<LevelStem>>> tags = new HashMap<>();
        original.listTags().forEach(tag -> {
            List<Holder<LevelStem>> values = new ArrayList<>();
            tag.forEach(holder -> holder.unwrapKey().ifPresent(key ->
                replacement.get(key).ifPresent(values::add)));
            tags.put(tag.key(), values);
        });
        replacement.bindTags(tags);
        return replacement.freeze();
    }

    private static void replaceRegistryAndSavedSettings(MinecraftServer server, Registry<LevelStem> registry) {
        var registries = new ArrayList<Registry<?>>();
        server.registries().getLayer(RegistryLayer.DIMENSIONS).registries().forEach(entry ->
            registries.add(entry.key().equals(Registries.LEVEL_STEM) ? registry : entry.value()));
        var layer = new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
        ((DimensionServerAccess) server).dimlib_registries(server.registries().replaceFrom(
            RegistryLayer.DIMENSIONS, layer, server.registries().getLayer(RegistryLayer.RELOADABLE)));
        var settings = server.getWorldGenSettings();
        ((DimensionSettingsAccess) settings).dimlib_dimensions(new WorldDimensions(registry));
        settings.setDirty();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Dimension changes must run on the server thread");
    }
}

package qouteall.dimlib.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import qouteall.dimlib.DimensionImpl;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.DynamicDimensionsImpl;
import qouteall.imm_ptl.core.ClientWorldLoader;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** The DimLib API adapted to Minecraft 26.2 identifiers and native saved world data. */
public final class DimensionAPI {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private DimensionAPI() {}

    @FunctionalInterface public interface ServerDimensionsLoadCallback { void run(MinecraftServer server); }
    @FunctionalInterface public interface ServerDynamicUpdateListener {
        void run(MinecraftServer server, Set<ResourceKey<Level>> dimensions);
    }
    @FunctionalInterface public interface ClientDynamicUpdateListener { void run(Set<ResourceKey<Level>> dimensions); }
    @FunctionalInterface public interface PreRemoveDimensionCallback { void accept(ServerLevel world); }

    /** Native base worlds are ready; listeners may add dimensions before the dimension stack is applied. */
    public static final Event<ServerDimensionsLoadCallback> SERVER_DIMENSIONS_LOAD_EVENT =
        EventFactory.createArrayBacked(ServerDimensionsLoadCallback.class,
            callbacks -> server -> { for (var callback : callbacks) {
                try { callback.run(server); } catch (Exception error) { LOGGER.error("Dimension load listener failed", error); }
            } });
    public static final Event<ServerDynamicUpdateListener> SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT =
        EventFactory.createArrayBacked(ServerDynamicUpdateListener.class,
            callbacks -> (server, dimensions) -> { for (var callback : callbacks) {
                try { callback.run(server, dimensions); } catch (Exception error) { LOGGER.error("Dimension update listener failed", error); }
            } });
    public static final Event<ClientDynamicUpdateListener> CLIENT_DIMENSION_UPDATE_EVENT =
        EventFactory.createArrayBacked(ClientDynamicUpdateListener.class,
            callbacks -> dimensions -> { for (var callback : callbacks) {
                try { callback.run(dimensions); } catch (Exception error) { LOGGER.error("Client dimension listener failed", error); }
            } });
    public static final Event<PreRemoveDimensionCallback> SERVER_PRE_REMOVE_DIMENSION_EVENT =
        EventFactory.createArrayBacked(PreRemoveDimensionCallback.class,
            callbacks -> world -> { for (var callback : callbacks) {
                try { callback.accept(world); } catch (Exception error) { LOGGER.error("Dimension removal listener failed", error); }
            } });

    /** Call on the server thread. The new generator definition and chunks survive restart. */
    public static void addDimension(MinecraftServer server, Identifier dimensionId, LevelStem levelStem) {
        DynamicDimensionsImpl.addDimensionDynamically(server, dimensionId, levelStem);
    }

    public static void addDimensionDynamically(MinecraftServer server, Identifier dimensionId, LevelStem levelStem) {
        DynamicDimensionsImpl.addDimensionDynamically(server, dimensionId, levelStem);
    }

    public static boolean dimensionExistsInRegistry(MinecraftServer server, Identifier dimensionId) {
        return DimensionImpl.getDimensionRegistry(server).containsKey(dimensionId);
    }

    public static void addDimensionIfNotExists(
        MinecraftServer server, Identifier dimensionId, Supplier<LevelStem> levelStem
    ) {
        if (!dimensionExistsInRegistry(server, dimensionId)) addDimension(server, dimensionId, levelStem.get());
    }

    /** Removal runs at the end of the server tick, evacuates players and retains all world files. */
    public static void removeDimensionDynamically(ServerLevel world) {
        DynamicDimensionsImpl.removeDimensionDynamically(world);
    }

    public static boolean isDimensionAlive(ServerLevel world) {
        return world.getServer().getLevel(world.dimension()) == world;
    }

    public static void suppressExperimentalWarning() { DimensionImpl.suppressExperimentalWarning = true; }
    public static void suppressExperimentalWarningForNamespace(String namespace) {
        DimensionImpl.STABLE_NAMESPACES.add(namespace);
    }

    @Environment(EnvType.CLIENT)
    public static Set<ResourceKey<Level>> getClientDimensionIds() {
        return Set.copyOf(getClientDimensionIdToTypeMap().keySet());
    }

    @Environment(EnvType.CLIENT)
    public static Map<ResourceKey<Level>, ResourceKey<DimensionType>> getClientDimensionIdToTypeMap() {
        var mapping = ClientWorldLoader.dimIdToDimTypeId;
        if (mapping == null) throw new IllegalStateException("Dimension information has not been synchronized");
        return Map.copyOf(mapping);
    }

    public static void registerDimensionTemplate(String name, DimensionTemplate template) {
        DimensionTemplate.registerDimensionTemplate(name, template);
    }
}

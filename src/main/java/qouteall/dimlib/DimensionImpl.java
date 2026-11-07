package qouteall.dimlib;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Compatibility policy for the original Apache-2.0 DimLib public API. */
public final class DimensionImpl {
    public static final Set<String> STABLE_NAMESPACES = ConcurrentHashMap.newKeySet();
    public static volatile boolean suppressExperimentalWarning;

    private DimensionImpl() {}

    public static boolean isStableNamespace(String namespace) {
        return suppressExperimentalWarning || STABLE_NAMESPACES.contains(namespace);
    }

    public static Registry<LevelStem> getDimensionRegistry(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
    }
}

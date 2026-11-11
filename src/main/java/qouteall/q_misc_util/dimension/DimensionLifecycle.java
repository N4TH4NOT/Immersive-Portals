package qouteall.q_misc_util.dimension;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import qouteall.q_misc_util.Helper;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Dimension lifecycle of vanilla/Fabric worlds, independent of optional dynamic-dimension mods. */
public final class DimensionLifecycle {
    private DimensionLifecycle() {}

    public static final Event<BiConsumer<MinecraftServer, Set<ResourceKey<Level>>>>
            SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT = Helper.createBiConsumerEvent();
    public static final Event<Consumer<ServerLevel>> SERVER_PRE_REMOVE_DIMENSION_EVENT =
            Helper.createConsumerEvent();
    public static final Event<Consumer<MinecraftServer>> SERVER_DIMENSIONS_LOAD_EVENT =
            Helper.createConsumerEvent();
    public static final Event<Consumer<Set<ResourceKey<Level>>>> CLIENT_DIMENSION_UPDATE_EVENT =
            Helper.createConsumerEvent();

    public static void init() {
        ServerLevelEvents.UNLOAD.register(
                (server, world) -> SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world));
    }
}

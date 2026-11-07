package qouteall.dimlib;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.q_misc_util.dimension.DimensionLifecycle;

/** Embedded library entry point; networking is shared with Immersive Portals. */
public final class DimLibEntry implements ModInitializer {
    @Override public void onInitialize() {
        DimensionTemplate.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> DimsCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(DynamicDimensionsImpl::processRemovals);
        ServerLifecycleEvents.SERVER_STOPPED.register(DynamicDimensionsImpl::clearServer);
        DimensionLifecycle.SERVER_DIMENSIONS_LOAD_EVENT.register(
            server -> DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.invoker().run(server));
        DimensionLifecycle.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.register(
            (server, dimensions) -> DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, dimensions));
        DimensionLifecycle.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(
            world -> {
                if (DynamicDimensionsImpl.isRemoving(world))
                    DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world);
            });
    }
}

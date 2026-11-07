package qouteall.dimlib;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.mixin.DimensionClientAccess;
import qouteall.q_misc_util.dimension.DimensionLifecycle;

import java.util.HashSet;

public final class DimLibEntryClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        DimensionLifecycle.CLIENT_DIMENSION_UPDATE_EVENT.register(dimensions -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) ((DimensionClientAccess) connection).dimlib_levels(new HashSet<>(dimensions));
            DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.invoker().run(dimensions);
        });
    }
}

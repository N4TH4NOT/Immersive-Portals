package qouteall.imm_ptl.core.platform_specific.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;

@Mixin(ServerPlayer.class)
public class MixinServerPlayerEntity_MA {
    @WrapMethod(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;")
    private ServerPlayer ip_resyncAfterVanillaDimensionChange(
        TeleportTransition transition, Operation<ServerPlayer> original
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        var previousWorld = player.level();
        ServerPlayer result = original.call(transition);
        if (result != null && result.level() != previousWorld) {
            // Vanilla respawn replaces every client world. Pairings retained for
            // remote dimensions would otherwise suppress their spawn/chunk packets.
            // Send the reset and rebuilt views after the respawn packet, so none
            // of their newly delivered entities is discarded by client cleanup.
            ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(result);
            ImmPtlChunkTracking.immediatelyUpdateForPlayer(result);
        }
        return result;
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
    private void onChangeDimensionByVanilla(
        TeleportTransition dimensionTransition, CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ServerPlayer this_ = (ServerPlayer) (Object) this;
        // Unlike 1.21's changeDimension, teleport also handles moves within one level.
        if (dimensionTransition.newLevel() == this_.level()) return;
        onBeforeDimensionTravel(this_);
    }
    
    private static void onBeforeDimensionTravel(ServerPlayer player) {
        CustomPortalGenManager customPortalGenManager =
            IPPerServerInfo.of(player.level().getServer()).customPortalGenManager;
        
        if (customPortalGenManager != null) {
            customPortalGenManager.onBeforeConventionalDimensionChange(player);
            
            ServerTaskList.of(player.level().getServer()).addTask(() -> {
                customPortalGenManager.onAfterConventionalDimensionChange(player);
                return true;
            });
        }
    }
}

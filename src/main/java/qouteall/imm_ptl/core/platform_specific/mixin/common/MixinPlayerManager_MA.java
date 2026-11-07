package qouteall.imm_ptl.core.platform_specific.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

@Mixin(PlayerList.class)
public class MixinPlayerManager_MA {
    @Inject(
        method = "respawn",
        at = @At("HEAD")
    )
    private void onPlayerRespawn(
        ServerPlayer oldPlayer, boolean bl, Entity.RemovalReason removalReason,
        CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(oldPlayer);
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void ip_resyncRespawnedPlayer(
        ServerPlayer oldPlayer, boolean keepEverything, Entity.RemovalReason removalReason,
        CallbackInfoReturnable<ServerPlayer> cir
    ) {
        // Also rebuild remote views when death creates a fresh player in the same
        // dimension. The old player's pairings were cleared at method entry.
        ImmPtlChunkTracking.immediatelyUpdateForPlayer(cir.getReturnValue());
    }
    
    @Inject(
        method = "remove",
        at = @At("HEAD")
    )
    private void onPlayerDisconnect(ServerPlayer player, CallbackInfo ci) {
        ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(player);
    }
}

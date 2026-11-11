package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickup;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

/** Pickup packets belong to the taken entity's tracking world, including remote viewers. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_Pickup {
    @Inject(method = "take", at = @At("HEAD"), cancellable = true)
    private void ip_sendRemotePickupAnchor(Entity item, int count, CallbackInfo ci) {
        var context = CrossPortalPickup.currentPickup();
        if (context == null || context.player() != (Object) this || context.item() != item
                || item.isRemoved()
                || !(item.level() instanceof ServerLevel itemWorld)) return;
        var collector = context.player();
        Vec3 anchor = context.portal().transformPoint(new Vec3(collector.getX(),
                (collector.getY() + collector.getEyeY()) / 2, collector.getZ()));
        // IDs alone cannot locate a collector in an untracked world. Give every
        // observer the authoritative target in the item's own coordinate space.
        itemWorld.getChunkSource().sendToTrackingPlayers(item,
                McRemoteProcedureCall.createPacketToSendToClient(
                        "qouteall.imm_ptl.core.block_manipulation.CrossPortalPickupClient.RemoteCallables.take",
                        itemWorld.dimension(), item.getId(), collector.getId(), count, anchor));
        ci.cancel();
    }

    @Redirect(method = "take", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;"))
    private ServerChunkCache ip_pickupTrackingWorld(ServerLevel collectorWorld,
            Entity item, int count) {
        return item.level() instanceof ServerLevel itemWorld
                ? itemWorld.getChunkSource() : collectorWorld.getChunkSource();
    }
}

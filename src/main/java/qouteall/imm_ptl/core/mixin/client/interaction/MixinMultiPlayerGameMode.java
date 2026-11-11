package qouteall.imm_ptl.core.mixin.client.interaction;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.imm_ptl.core.ducks.IEClientPlayerInteractionManager;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode implements IEClientPlayerInteractionManager {
    @Shadow
    @Final
    private ClientPacketListener connection;
    
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow private boolean isDestroying;
    @Unique private ResourceKey<Level> ip_destroyDimension;
    @Unique private java.util.List<java.util.UUID> ip_destroyPath = java.util.List.of();

    @Inject(method = "startDestroyBlock", at = @At("RETURN"))
    private void ip_rememberDestroyWorld(net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction face, CallbackInfoReturnable<Boolean> ci) {
        if (isDestroying) {
            ip_destroyDimension = minecraft.level.dimension();
            ip_destroyPath = java.util.List.copyOf(
                    qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remotePortalPath);
        }
    }

    @Inject(method = "sameDestroyTarget", at = @At("HEAD"), cancellable = true)
    private void ip_distinguishDestroyWorld(net.minecraft.core.BlockPos pos,
            CallbackInfoReturnable<Boolean> ci) {
        if (ip_destroyDimension != null && ip_destroyDimension != minecraft.level.dimension())
            ci.setReturnValue(false);
    }

    @WrapMethod(method = "stopDestroyBlock")
    private void ip_abortInDestroyWorld(Operation<Void> original) {
        var previousPath = qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remotePortalPath;
        try {
            if (isDestroying && ip_destroyDimension != null
                    && (ip_destroyDimension != minecraft.level.dimension() || !ip_destroyPath.isEmpty())) {
                var world = ClientWorldLoader.getOptionalWorld(ip_destroyDimension);
                if (world != null) {
                    qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remotePortalPath = ip_destroyPath;
                    ClientWorldLoader.withSwitchedWorld(world, () -> { original.call(); });
                    return;
                }
            }
            original.call();
        } finally {
            qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remotePortalPath = previousPath;
            if (!isDestroying) {
                ip_destroyDimension = null;
                ip_destroyPath = java.util.List.of();
            }
        }
    }

    @WrapMethod(method = "performUseItemOn")
    private net.minecraft.world.InteractionResult ip_placeWithTransformedRotation(LocalPlayer player,
            InteractionHand hand, BlockHitResult hit, Operation<net.minecraft.world.InteractionResult> original) {
        return ClientWorldLoader.getIsWorldSwitched()
                ? qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.withRemoteRotation(
                        () -> original.call(player, hand, hit))
                : original.call(player, hand, hit);
    }

    @WrapMethod(method = "attack")
    private void ip_predictRemoteAttackRotation(Player player, net.minecraft.world.entity.Entity target,
            Operation<Void> original) {
        if (ClientWorldLoader.getIsWorldSwitched())
            qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.withRemoteRotation(
                    () -> original.call(player, target));
        else original.call(player, target);
    }

    @WrapMethod(method = "interact")
    private net.minecraft.world.InteractionResult ip_predictRemoteUseRotation(Player player,
            net.minecraft.world.entity.Entity target, net.minecraft.world.phys.EntityHitResult hit,
            InteractionHand hand, Operation<net.minecraft.world.InteractionResult> original) {
        return ClientWorldLoader.getIsWorldSwitched()
                ? qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.withRemoteRotation(
                        () -> original.call(player, target, hit, hand))
                : original.call(player, target, hit, hand);
    }
    
    // the player level field is not being switched now
    @Redirect(
        method = "lambda$startDestroyBlock$1", // lambda in startDestroyBlock
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;level()Lnet/minecraft/world/level/Level;"
        )
    )
    private Level redirectPlayerLevel1(LocalPlayer instance) {
        return Minecraft.getInstance().level;
    }
    
    // the player level field is not being switched now
    @Redirect(
        method = "continueDestroyBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;level()Lnet/minecraft/world/level/Level;"
        )
    )
    private Level redirectPlayerLevel2(LocalPlayer instance) {
        return Minecraft.getInstance().level;
    }
    
    // use another constructor that does not use player level
    @Redirect(
        method = "performUseItemOn",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/item/context/UseOnContext;"
        )
    )
    private UseOnContext redirectNewUseOnContext(Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        return new UseOnContext(
            Minecraft.getInstance().level,
            player,
            interactionHand,
            player.getItemInHand(interactionHand),
            blockHitResult
        );
    }
    
    @ModifyArg(
        method = "startPrediction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private Packet<?> modifyPacketInStartPrediction(Packet<?> packet) {
        return ip_redirectPacket(packet);
    }
    
    @ModifyArg(
        method = "startDestroyBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private Packet redirectSendInStartDestroyBlock(Packet packet) {
        return ip_redirectPacket(packet);
    }
    
    @ModifyArg(
        method = "stopDestroyBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private Packet redirectSendInStopDestroyBlock(Packet packet) {
        return ip_redirectPacket(packet);
    }

    @ModifyArg(
        method = {"attack", "interact"},
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V")
    )
    private Packet<?> ip_redirectEntityInteraction(Packet<?> packet) {
        return ip_redirectPacket(packet);
    }
    
    private static Packet<?> ip_redirectPacket(Packet<?> packet) {
        if (ClientWorldLoader.getIsWorldSwitched()) {
            if (qouteall.imm_ptl.core.network.bridge.BridgeClient.isActive()) {
                return qouteall.imm_ptl.core.network.bridge.BridgeClient.interaction(packet,qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remotePortalPath);
            }
            ResourceKey<Level> dimension = Minecraft.getInstance().level.dimension();
            var path = qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient
                    .remotePortalPath.toArray(java.util.UUID[]::new);
            var target = qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient.remoteHitResult;
            var point = target == null ? net.minecraft.world.phys.Vec3.ZERO : target.getLocation();
            if (packet instanceof ServerboundPlayerActionPacket playerActionPacket) {
                if (BlockManipulationServer.isAttackingAction(playerActionPacket.getAction())) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    ServerboundPlayerActionPacket.STREAM_CODEC.encode(buf, playerActionPacket);
                    
                    return ip_createRemotePacket(
                        "qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processPlayerActionPacket",
                        dimension,
                        path,
                        point,
                        buf
                    );
                }
            }
            else if (packet instanceof ServerboundUseItemOnPacket useItemOnPacket) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                ServerboundUseItemOnPacket.STREAM_CODEC.encode(buf, useItemOnPacket);
                
                return ip_createRemotePacket(
                    "qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processUseItemOnPacket",
                    dimension,
                    path,
                    point,
                    buf
                );
            }
            else if (packet instanceof net.minecraft.network.protocol.game.ServerboundAttackPacket attack) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                net.minecraft.network.protocol.game.ServerboundAttackPacket.STREAM_CODEC.encode(buf, attack);
                return ip_createRemoteEntityPacket(
                        "qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processEntityAttackPacket",
                        dimension, path, point, buf);
            }
            else if (packet instanceof net.minecraft.network.protocol.game.ServerboundInteractPacket use) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                net.minecraft.network.protocol.game.ServerboundInteractPacket.STREAM_CODEC.encode(buf, use);
                return ip_createRemoteEntityPacket(
                        "qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processEntityUsePacket",
                        dimension, path, point, buf);
            }
            // ServerboundUseItemPacket is not redirected
        }
        
        return packet;
    }

    @Unique
    private static Packet<?> ip_createRemoteEntityPacket(String method, ResourceKey<Level> dimension,
            java.util.UUID[] path, net.minecraft.world.phys.Vec3 point, FriendlyByteBuf buffer) {
        var projection = qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient
                .remoteProjectionPortal;
        var ids = projection == null ? new java.util.UUID[0]
                : new java.util.UUID[] {projection.getUUID()};
        try {
            return McRemoteProcedureCall.createPacketToSendToServer(
                    method, dimension, path, ids, point, IPMcHelper.bufToBytes(buffer));
        } finally { buffer.release(); }
    }

    @Unique
    private static Packet<?> ip_createRemotePacket(String method, ResourceKey<Level> dimension,
            java.util.UUID[] path, net.minecraft.world.phys.Vec3 point, FriendlyByteBuf buffer) {
        try {
            return McRemoteProcedureCall.createPacketToSendToServer(
                    method, dimension, path, point, IPMcHelper.bufToBytes(buffer));
        } finally { buffer.release(); }
    }
    
}

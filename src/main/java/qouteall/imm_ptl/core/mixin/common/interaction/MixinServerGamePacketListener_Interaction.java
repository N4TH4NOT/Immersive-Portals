package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.block_manipulation.PortalInteractionServer;

/** Vanilla entity handlers retain their checks while operating on a validated remote target. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListener_Interaction {
    @org.spongepowered.asm.mixin.Shadow public ServerPlayer player;

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "updateSignText")
    private void ip_filterIntoOriginalSignWorld(
            net.minecraft.network.protocol.game.ServerboundSignUpdatePacket packet,
            java.util.List<net.minecraft.server.network.FilteredText> lines, Operation<Void> original) {
        qouteall.imm_ptl.core.block_manipulation.SignInteractionServer.applyFiltered(player, packet,
                () -> original.call(packet, lines));
    }

    @Redirect(method = "updateSignText", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel ip_filteredSignWorld(ServerPlayer player) {
        var world = qouteall.imm_ptl.core.block_manipulation.SignInteractionServer.applyingWorld();
        return world == null ? player.level() : world;
    }

    @Redirect(method = {"handleAttack", "handleInteract", "handleUseItemOn"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel ip_targetWorld(ServerPlayer player) {
        ServerLevel remote = PortalInteractionServer.entityWorld();
        return remote == null ? player.level() : remote;
    }

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean ip_blockUseReach(ServerPlayer player, net.minecraft.core.BlockPos block,
            double padding, Operation<Boolean> original) {
        return PortalInteractionServer.hasValidatedBlock() || original.call(player, block, padding);
    }

    @WrapOperation(method = "handleAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;isWithinAttackRange(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/AABB;D)Z"))
    private boolean ip_attackReach(ServerPlayer player, ItemStack stack, AABB box,
            double padding, Operation<Boolean> original) {
        return PortalInteractionServer.hasValidatedEntity()
                || original.call(player, stack, box, padding);
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z"))
    private boolean ip_useReach(ServerPlayer player, AABB box, double padding,
            Operation<Boolean> original) {
        return PortalInteractionServer.hasValidatedEntity() || original.call(player, box, padding);
    }

    @WrapOperation(method = "handleAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;attack(Lnet/minecraft/world/entity/Entity;)V"))
    private void ip_attackDirection(ServerPlayer player, Entity target, Operation<Void> original) {
        PortalInteractionServer.withAttackRotation(player, () -> original.call(player, target));
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;"))
    private net.minecraft.world.InteractionResult ip_useDirection(ServerPlayer player, Entity entity,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 point,
            Operation<net.minecraft.world.InteractionResult> original) {
        var target = PortalInteractionServer.currentEntityTarget();
        return PortalInteractionServer.isValidatedEntity(entity)
                ? qouteall.imm_ptl.core.block_manipulation.PortalEntityActionContext.withUseRotation(
                        player, target, () -> original.call(player, entity, hand, point))
                : original.call(player, entity, hand, point);
    }
}

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
    @Redirect(method = {"handleAttack", "handleInteract"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel ip_targetWorld(ServerPlayer player) {
        ServerLevel remote = PortalInteractionServer.entityWorld();
        return remote == null ? player.level() : remote;
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
}

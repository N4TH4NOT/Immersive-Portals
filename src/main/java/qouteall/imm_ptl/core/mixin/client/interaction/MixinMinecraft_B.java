package qouteall.imm_ptl.core.mixin.client.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_B {
    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean ip_remoteEntityReach(net.minecraft.client.player.LocalPlayer player,
        net.minecraft.world.entity.Entity entity, double padding, Operation<Boolean> original) {
        // The picker measures reach along the transformed ray. Source-world coordinates
        // cannot be compared directly with an entity in the destination world.
        return BlockManipulationClient.isRemoteEntity(entity)
                ? BlockManipulationClient.isRemoteEntityWithinInteractionRange(entity)
                : original.call(player, entity, padding);
    }

    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/component/AttackRange;isInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean ip_remoteAttackReach(net.minecraft.world.item.component.AttackRange range,
        net.minecraft.world.entity.LivingEntity player, net.minecraft.world.phys.Vec3 position,
        Operation<Boolean> original) {
        return BlockManipulationClient.remoteHitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && BlockManipulationClient.isRemoteEntity(hit.getEntity())
                ? BlockManipulationClient.isRemoteEntityWithinAttackRange(range)
                : original.call(range, player, position);
    }

    @Shadow
    protected abstract void pickBlockOrEntity();
    
    @Shadow
    public ClientLevel level;
    
    @Shadow
    public HitResult hitResult;
    
    @Shadow
    protected int missTime;
    
    @Shadow
    protected abstract boolean startAttack();
    
    @Shadow
    protected abstract void continueAttack(boolean leftClick);
    
    @Shadow
    protected abstract void startUseItem();
    
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;startAttack()Z"
        )
    )
    private boolean wrapStartAttack(Minecraft instance, Operation<Boolean> original) {
        ClientLevel remoteWorld = BlockManipulationClient.getRemotePointedWorld();
        if (BlockManipulationClient.isPointingToPortal()) {
            return BlockManipulationClient.withSwitchedContext(
                () -> original.call(instance),
                false
            );
        }
        else {
            return original.call(instance);
        }
    }
    
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"
        )
    )
    private void wrapContinueAttack(Minecraft instance, boolean leftClick, Operation<Void> original) {
        if (BlockManipulationClient.isPointingToPortal()) {
            BlockManipulationClient.withSwitchedContext(
                () -> {
                    original.call(instance, leftClick);
                    return null;
                },
                false
            );
        }
        else {
            original.call(instance, leftClick);
        }
    }
    
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;startUseItem()V"
        )
    )
    private void wrapStartUseItem(Minecraft instance, Operation<Void> original) {
        if (BlockManipulationClient.isPointingToPortal()) {
            BlockManipulationClient.withSwitchedContext(
                () -> {
                    original.call(instance);
                    return null;
                },
                true
            );
            
        }
        else {
            original.call(instance);
        }
    }
    
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;pickBlockOrEntity()V"
        )
    )
    private void wrapPickBlock(Minecraft instance, Operation<Void> original) {
        if (BlockManipulationClient.isPointingToPortal()) {
            ClientLevel remoteWorld = ClientWorldLoader.getWorld(BlockManipulationClient.remotePointedDim);
            ClientLevel oldWorld = this.level;
            HitResult oldTarget = this.hitResult;
            
            level = remoteWorld;
            hitResult = BlockManipulationClient.remoteHitResult;
            
            try {
                original.call(instance);
            }
            finally {
                level = oldWorld;
                hitResult = oldTarget;
            }
        }
        else {
            original.call(instance);
        }
    }
}

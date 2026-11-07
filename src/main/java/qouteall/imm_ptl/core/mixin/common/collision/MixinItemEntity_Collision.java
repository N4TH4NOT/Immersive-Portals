package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity_Collision {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean ip_testPhysicalItemBody(Level world, Entity entity, AABB box,
            Operation<Boolean> original) {
        // An intact backing wall behind the clipped body must not activate
        // vanilla's random push-out or its temporary noPhysics movement.
        AABB active = ((IEEntity) entity).ip_getActiveCollisionBox(box);
        return active == null || original.call(world, entity, active);
    }
}

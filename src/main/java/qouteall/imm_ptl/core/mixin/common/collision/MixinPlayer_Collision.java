package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import qouteall.imm_ptl.core.ducks.IEEntity;

@SuppressWarnings("resource")
@Mixin(Player.class)
public abstract class MixinPlayer_Collision {

    // Keep the native pose check available to Gravity Changer's directional box wrapper.
    @WrapOperation(
            method = "canPlayerFitWithinBlocksAndEntitiesWhen",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean ip_poseCollision(
            Level world, Entity entity, AABB box, Operation<Boolean> original) {
        AABB activeCollisionBox = ((IEEntity) entity).ip_getActiveCollisionBox(box);
        return activeCollisionBox == null || original.call(world, entity, activeCollisionBox);
    }
}

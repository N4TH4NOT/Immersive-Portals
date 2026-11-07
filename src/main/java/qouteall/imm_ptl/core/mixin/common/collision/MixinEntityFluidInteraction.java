package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import qouteall.imm_ptl.core.collision.PortalFluidInteraction;

/** Overrides only native sampling geometry; entity position, world and motion never change. */
@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {
    @WrapMethod(method = "update")
    private void ip_samplePortalBody(Entity entity, boolean ignoreCurrent, Operation<Void> original) {
        PortalFluidInteraction.update((EntityFluidInteraction) (Object) this, entity,
                ignoreCurrent, () -> original.call(entity, ignoreCurrent));
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;getFluidInteractionBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB ip_sampleBox(Entity entity, Operation<AABB> original) {
        var frame = PortalFluidInteraction.frame(entity);
        return frame == null ? original.call(entity) : frame.sample();
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;level()Lnet/minecraft/world/level/Level;"))
    private Level ip_sampleWorld(Entity entity, Operation<Level> original) {
        var frame = PortalFluidInteraction.frame(entity);
        return frame == null ? original.call(entity) : frame.world();
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB ip_fullBodyBox(Entity entity, Operation<AABB> original) {
        var frame = PortalFluidInteraction.frame(entity);
        return frame == null ? original.call(entity) : frame.body();
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;getEyeY()D"))
    private double ip_eyeHeight(Entity entity, Operation<Double> original) {
        var frame = PortalFluidInteraction.frame(entity);
        return frame == null ? original.call(entity) : frame.eye().y;
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;getBlockX()I"))
    private int ip_bodyX(Entity entity, Operation<Integer> original) {
        var frame = PortalFluidInteraction.frame(entity);
        return frame == null ? original.call(entity) : Mth.floor(frame.feet().x);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;getBlockZ()I"))
    private int ip_bodyZ(Entity entity, Operation<Integer> original) {
        var frame = PortalFluidInteraction.frame(entity);
        return frame == null ? original.call(entity) : Mth.floor(frame.feet().z);
    }
}

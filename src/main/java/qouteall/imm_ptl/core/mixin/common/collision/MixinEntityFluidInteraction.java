package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import qouteall.imm_ptl.core.collision.PortalFluidInteraction;
import qouteall.imm_ptl.core.collision.PortalFluidForces;

/** Overrides only native sampling geometry; entity position, world and motion never change. */
@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {
    @WrapMethod(method = "applyCurrentTo")
    private void ip_applyPortalFluidCurrent(TagKey<Fluid> fluid, Entity entity, double strength,
            Operation<Void> original) {
        PortalFluidForces.apply((EntityFluidInteraction) (Object) this, fluid, entity,
                strength, () -> original.call(fluid, entity, strength));
    }

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

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState ip_sampleSourcePhysicalUnion(BlockGetter world, BlockPos position,
            Operation<FluidState> original) {
        FluidState state = original.call(world, position);
        var frame = PortalFluidInteraction.currentFrame();
        if (frame == null || frame.physicalParts() == null || state.isEmpty()) return state;
        AABB fluid = new AABB(position.getX(), position.getY(), position.getZ(),
                position.getX() + 1, position.getY() + state.getHeight(world, position), position.getZ() + 1);
        // The native loop still visits each cell once and retains its own ordering,
        // height accumulation and current cutoff. Only fully removed fluid is absent.
        return frame.physicalParts().stream().anyMatch(part -> part.intersects(fluid))
                ? state : Fluids.EMPTY.defaultFluidState();
    }
}

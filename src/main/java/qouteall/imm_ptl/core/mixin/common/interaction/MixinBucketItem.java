package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.block_manipulation.PortalInteractionTarget;

@Mixin(BucketItem.class)
public class MixinBucketItem {
    @Redirect(
        method = "use",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BucketItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    private BlockHitResult redirectRayTrace(
        Level level, Player player, ClipContext.Fluid fluid,
        @Local(argsOnly = true) LocalRef<Level> worldRef
    ) {
        var target = PortalInteractionTarget.traceBlocks(player, fluid);
        if (target != null && target.hit() instanceof BlockHitResult hit) {
            worldRef.set(target.world());
            return hit;
        }
        Vec3 view = player.getViewVector(1);
        Vec3 end = player.getEyePosition().add(view.scale(player.blockInteractionRange()));
        return BlockHitResult.miss(end, Direction.getApproximateNearest(view.x, view.y, view.z),
                BlockPos.containing(end));
    }
}

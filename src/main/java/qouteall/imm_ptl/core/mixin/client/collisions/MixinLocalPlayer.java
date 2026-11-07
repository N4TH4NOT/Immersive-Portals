package qouteall.imm_ptl.core.mixin.client.collisions;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Unique private static int ip_contactDiagnostics;
    @Unique private Vec3 ip_contactVelocity;
    @Unique private Vec3 ip_contactPosition;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void ip_beforeContactForces(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        ip_contactVelocity = player.getDeltaMovement();
        ip_contactPosition = player.position();
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void ip_reportContactForces(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!Boolean.getBoolean("imm_ptl.renderDiagnostics")
            || System.getProperty("portalSmoke.target") == null || ip_contactDiagnostics >= 24
            || !((IEEntity) player).ip_isRecentlyCollidingWithPortal()
            || ip_contactVelocity == null || ip_contactPosition == null
            || Math.abs(player.getDeltaMovement().x) < 1.0E-6) return;
        ip_contactDiagnostics++;
        var fluids = new java.util.ArrayList<String>();
        var box = player.getFluidInteractionBox();
        for (BlockPos position : BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var fluid = player.level().getFluidState(position);
            if (!fluid.isEmpty()) fluids.add(position.toShortString() + "=" + fluid);
        }
        var peers = player.level().getEntities(player, player.getBoundingBox().inflate(.25),
            entity -> entity.isPushable()).stream()
            .map(entity -> entity.getType() + "#" + entity.getId() + "@" + entity.position()).toList();
        System.out.println("PORTAL_CONTACT_FORCES tick=" + player.tickCount
            + " before=" + ip_contactPosition + " position=" + player.position()
            + " velocityBefore=" + ip_contactVelocity + " velocity=" + player.getDeltaMovement()
            + " water=" + player.isInWater() + " lava=" + player.isInLava()
            + " waterHeight=" + player.getFluidHeight(FluidTags.WATER)
            + " lavaHeight=" + player.getFluidHeight(FluidTags.LAVA)
            + " fluids=" + fluids + " peers=" + peers);
    }

    // avoid beingpushed out of blocks by the blocks on the other facing of the portal
    @Inject(
        method = "Lnet/minecraft/client/player/LocalPlayer;suffocatesAt(Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCannotFitAt(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (((IEEntity) this).ip_isRecentlyCollidingWithPortal()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}

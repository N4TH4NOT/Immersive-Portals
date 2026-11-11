package qouteall.imm_ptl.core.mixin.client;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.ducks.IEEntity;
@Mixin(InterpolationHandler.class)
public abstract class MixinLivingEntity_C {
    @Shadow @Final private Entity entity;
    @Shadow public abstract void cancel();
    @Inject(method="interpolateTo",at=@At("HEAD"),cancellable=true)
    private void ip_interpolate(Vec3 pos,float yaw,float pitch,CallbackInfo ci) {
        if (!IPGlobal.allowClientEntityPosInterpolation ||
            (((IEEntity)entity).ip_getCollidingPortal()!=null && entity.position().distanceToSqr(pos)>4)) {
            McHelper.setPosAndLastTickPos(entity,pos,pos.subtract(McHelper.getWorldVelocity(entity)));
            entity.setYRot(yaw); entity.setXRot(pitch);
            McHelper.updateBoundingBox(entity); cancel(); ci.cancel();
        }
    }
}

package qouteall.imm_ptl.core.mixin.client.render.optimization;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import qouteall.imm_ptl.core.ducks.IEFrustum;
import qouteall.imm_ptl.core.render.FrustumCuller;

/** Copies a frozen portal frustum along with the native frustum, including camera offsets. */
@Mixin(Frustum.class)
public abstract class MixinFrustum implements IEFrustum {
    @Shadow private Vector4f viewVector;
    @Unique private FrustumCuller ip_portalCull;

    @Inject(method = "prepare", at = @At("RETURN"))
    private void ip_prepare(double x, double y, double z, CallbackInfo ci) {
        ip_portalCull = new FrustumCuller();
        ip_portalCull.update(x, y, z);
    }

    @Inject(
            method = {"<init>(Lnet/minecraft/client/renderer/culling/Frustum;)V", "set"},
            at = @At("RETURN"))
    private void ip_copy(Frustum other, CallbackInfo ci) {
        FrustumCuller culler = ((MixinFrustum) (Object) other).ip_portalCull;
        ip_portalCull = culler == null ? null : culler.copy();
    }

    @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("HEAD"), cancellable = true)
    private void ip_cull(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            CallbackInfoReturnable<Integer> cir) {
        if (ip_portalCull != null
                && ip_portalCull.canDetermineInvisibleWithWorldCoord(
                        minX, minY, minZ, maxX, maxY, maxZ))
            cir.setReturnValue(FrustumIntersection.PLANE_NX);
    }

    @Inject(method = "calculateFrustum", at = @At("RETURN"))
    private void ip_normalize(CallbackInfo ci) {
        viewVector.normalize();
    }

    @Override
    public boolean ip_canDetermineInvisibleWithCamCoord(
            float a, float b, float c, float d, float e, float f) {
        return ip_portalCull != null
                && ip_portalCull.canDetermineInvisibleWithCameraCoord(a, b, c, d, e, f);
    }

    @Override
    public Vec3 ip_getViewVec3() {
        return new Vec3(viewVector.x, viewVector.y, viewVector.z);
    }
}

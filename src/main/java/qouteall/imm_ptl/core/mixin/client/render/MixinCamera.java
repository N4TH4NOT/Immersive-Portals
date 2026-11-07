package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import qouteall.imm_ptl.core.ducks.IECamera;

@Mixin(Camera.class)
public abstract class MixinCamera implements IECamera {
    @Shadow private Level level;
    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Override public void ip_resetState(Vec3 position, ClientLevel world) { setPosition(position); level = world; }
    @Override public void portal_setPos(Vec3 position) { setPosition(position); }
    @Override public float ip_getCameraY() { return eyeHeight; }
    @Override public float ip_getLastCameraY() { return eyeHeightOld; }
    @Override public void ip_setCameraY(float height, float old) { eyeHeight = height; eyeHeightOld = old; }
    @Override public void portal_setFocusedEntity(Entity value) { entity = value; }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
        method="getMaxZoom",
        at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",
            target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private net.minecraft.world.phys.BlockHitResult ip_cameraPortalCollision(
        Level world, net.minecraft.world.level.ClipContext context,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.world.phys.BlockHitResult> original
    ) {
        if (entity == null || !qouteall.imm_ptl.core.ClientWorldLoader.getIsInitialized())
            return original.call(world, context);
        return qouteall.imm_ptl.core.render.PortalCameraCollision.clip(
            world, ((Camera) (Object) this).position(), context, entity);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="getViewRotationMatrix", at=@org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable=true)
    private void ip_view(org.joml.Matrix4f out, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Matrix4f> cir) {
        Camera camera=(Camera)(Object)this;
        out.rotation(camera.rotation().conjugate(new org.joml.Quaternionf()));
        qouteall.imm_ptl.core.render.TransformationManager.applyAnimation(out);
        cir.setReturnValue(out);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="extractRenderState", at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void ip_cameraState(net.minecraft.client.renderer.state.level.CameraRenderState state, float partial, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        state.orientation.setFromUnnormalized(state.viewRotationMatrix).conjugate().normalize();
        if (qouteall.imm_ptl.core.render.TransformationManager.isIsometricView)
            state.projectionMatrix.set(qouteall.imm_ptl.core.render.TransformationManager.getIsometricProjection());
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="createProjectionMatrixForCulling", at=@org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable=true)
    private void ip_cullProjection(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Matrix4f> cir) {
        if (qouteall.imm_ptl.core.render.TransformationManager.isIsometricView)
            cir.setReturnValue(qouteall.imm_ptl.core.render.TransformationManager.getIsometricCullingProjection());
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="getViewRotationProjectionMatrix", at=@org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable=true)
    private void ip_isometricView(org.joml.Matrix4f out, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Matrix4f> cir) {
        if (qouteall.imm_ptl.core.render.TransformationManager.isIsometricView)
            cir.setReturnValue(out.set(qouteall.imm_ptl.core.render.TransformationManager.getIsometricProjection()).mul(((Camera)(Object)this).getViewRotationMatrix(new org.joml.Matrix4f())));
    }
}

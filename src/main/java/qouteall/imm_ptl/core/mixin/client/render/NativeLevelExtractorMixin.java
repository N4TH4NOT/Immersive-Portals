package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.render.*;

@Mixin(value = LevelExtractor.class, priority = 1100)
public abstract class NativeLevelExtractorMixin {
    @Shadow private ClientLevel level;
    @Shadow @Final private LevelRenderState levelRenderState;

    @ModifyExpressionValue(
            method = "extract",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
    private boolean ip_animatedFrustum(boolean original) {
        // Portal apertures and oblique views change independently of the camera's Euler angles.
        return original
                || NativeWorldRenderer.isRenderingScene()
                || qouteall.imm_ptl.core.IPGlobal.alwaysOverrideTerrainSetup;
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void ip_extract(DeltaTracker delta, Camera camera, float partial, CallbackInfo ci) {
        NativeWorldRenderer.applyChunkChanges((LevelExtractor) (Object) this, levelRenderState);
        NativeEntityPass.extract(levelRenderState, camera, partial);
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void ip_crossingBody(
            Entity entity,
            Frustum frustum,
            double x,
            double y,
            double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (qouteall.imm_ptl.core.IPGlobal.correctCrossPortalEntityRendering
                && ((IEEntity) entity).ip_getCollidingPortal() != null)
            cir.setReturnValue(false);
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void ip_dirty(int x, int y, int z, boolean playerChanged, CallbackInfo ci) {
        // Neighbor/range updates call this overload directly. Run before Sodium can
        // cancel vanilla processing, so every view of the same world is invalidated.
        if (level != null)
            NativeWorldRenderer.markSectionDirty((LevelExtractor) (Object) this, level, x, y, z);
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void ip_reload(CallbackInfo ci) {
        if (ClientWorldLoader.getIsInitialized()
                && (Object) this == Minecraft.getInstance().levelExtractor)
            NativeWorldRenderer.reload();
    }
}

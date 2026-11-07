package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import net.minecraft.client.*;
import net.minecraft.client.renderer.*;

import org.joml.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.portal.animation.*;
import qouteall.imm_ptl.core.render.*;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IEGameRenderer {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Mutable @Final private Camera mainCamera;
    @Shadow @Mutable @Final private Lightmap lightmap;

    @Inject(method = "update", at = @At("HEAD"))
    private void ip_beforeCamera(DeltaTracker delta, CallbackInfo ci) {
        if (NativeWorldRenderer.isRenderingScene()) return;
        IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.processTasks();
        if (minecraft.level == null || minecraft.getConnection() == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(true);
        RenderStates.setPartialTick(partial);
        StableClientTimer.update(minecraft.level.getGameTime(), partial);
        ClientPortalAnimationManagement.update();
        ClientTeleportationManager.manageTeleportation(false);
        RenderStates.updatePreRenderInfo(partial);
        IPGlobal.PRE_GAME_RENDER_EVENT.invoker().run();
        RenderStates.frameIndex++;
    }

    @Inject(method = "extractCamera", at = @At("RETURN"))
    private void ip_fog(CallbackInfo ci) {
        var cameraState =
                ((GameRenderer) (Object) this).gameRenderState().levelRenderState.cameraRenderState;
        if (qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering()
                && !MyGameRenderer.enablePortalCaveCulling) cameraState.smartCull = false;
        if (IPGlobal.alwaysOverrideTerrainSetup || MyGameRenderer.vanillaTerrainSetupOverride > 0) {
            minecraft.levelRenderer.sectionOcclusionGraph().invalidate();
            if (!NativeWorldRenderer.isRenderingScene()
                    && MyGameRenderer.vanillaTerrainSetupOverride > 0)
                MyGameRenderer.vanillaTerrainSetupOverride--;
        }
        if (!qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.isFogEnabled()) {
            var fog =
                    ((GameRenderer) (Object) this)
                            .gameRenderState()
                            .levelRenderState
                            .cameraRenderState
                            .fogData;
            fog.environmentalStart =
                    fog.renderDistanceStart =
                            fog.environmentalEnd =
                                    fog.renderDistanceEnd =
                                            fog.skyEnd = fog.cloudEnd = Float.MAX_VALUE;
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ip_beforeLevel(DeltaTracker delta, CallbackInfo ci) {
        if (!NativeWorldRenderer.isRenderingScene()) {
            PortalRenderer.switchToCorrectRenderer();
            IPCGlobal.renderer.prepareRendering();
        }
    }

    @WrapOperation(
            method = "renderLevel",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void ip_reduceWorldBobbing(
            GameRenderer renderer,
            CameraRenderState camera,
            PoseStack pose,
            Operation<Void> original) {
        // Isolate this contribution from hurt-camera transforms already on the stack.
        // The separate hand-rendering call to bobView keeps its original translation.
        PoseStack bob = new PoseStack();
        original.call(renderer, camera, bob);
        Matrix4f transform = bob.last().pose();
        float multiplier = (float) RenderStates.getViewBobbingOffsetMultiplier();
        transform.m30(transform.m30() * multiplier)
                .m31(transform.m31() * multiplier)
                .m32(transform.m32() * multiplier);
        pose.mulPose(transform);
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice ip_projection(
            ProjectionMatrixBuffer buffer, Matrix4f matrix, Operation<GpuBufferSlice> original) {
        NativeWorldRenderer.onProjection((GameRenderer) (Object) this, matrix);
        return original.call(buffer, matrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(value = "CONSTANT", args = "stringValue=hand"),
            cancellable = true)
    private void ip_afterScene(DeltaTracker delta, CallbackInfo ci) {
        if (NativeWorldRenderer.isRenderingScene()
                && !qouteall.imm_ptl.core.render.context_management.WorldRenderInfo
                        .getTopRenderInfo()
                        .doRenderHand) {
            net.minecraft.util.profiling.Profiler.get().pop();
            ci.cancel();
        }
    }

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void ip_guiPortals(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        GuiPortalRendering._onGameRenderEnd();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ip_renderEnd(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        if (minecraft.level != null && !NativeWorldRenderer.isRenderingScene()) {
            IPCGlobal.renderer.finishRendering();
            RenderStates.onTotalRenderEnd();
        }
    }

    @Override
    public void ip_setLightmapTextureManager(Lightmap value) {
        lightmap = value;
    }

    @Override
    public boolean ip_getDoRenderHand() {
        return !NativeWorldRenderer.isRenderingScene()
                || qouteall.imm_ptl.core.render.context_management.WorldRenderInfo
                        .getTopRenderInfo()
                        .doRenderHand;
    }

    @Override
    public void ip_setCamera(Camera camera) {
        mainCamera = camera;
    }

    @Override
    public void ip_setIsRenderingPanorama(boolean value) {
        if (value) mainCamera.enablePanoramicMode();
        else mainCamera.disablePanoramicMode();
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void ip_thirdPerson(
            LevelRenderer renderer,
            com.mojang.blaze3d.resource.GraphicsResourceAllocator pool,
            DeltaTracker delta,
            boolean outline,
            net.minecraft.client.renderer.state.level.CameraRenderState camera,
            Matrix4fc view,
            GpuBufferSlice fog,
            Vector4f color,
            boolean sky,
            Operation<Void> original) {
        if (!NativeWorldRenderer.isRenderingScene()
                && CrossPortalViewRendering.renderCrossPortalView()) return;
        original.call(
                renderer,
                pool,
                delta,
                outline,
                camera,
                view,
                fog,
                color,
                sky
                        && (!qouteall.imm_ptl.core.render.context_management.WorldRenderInfo
                                        .isRendering()
                                || qouteall.imm_ptl.core.render.context_management.WorldRenderInfo
                                        .getTopRenderInfo()
                                        .doRenderSky));
    }
}

package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.minecraft.client.Minecraft;

import org.joml.FrustumIntersection;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import qouteall.imm_ptl.core.compat.sodium_compatibility.PortalViewport;
import qouteall.imm_ptl.core.render.FrustumCuller;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Mixin(Viewport.class)
public abstract class MixinSodiumViewport implements PortalViewport {
    @Unique private FrustumCuller ip_culler;
    @Unique private boolean ip_caveCulling = true;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ip_snapshot(Frustum frustum, Vector3d position, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        ip_culler = new FrustumCuller();
        ip_culler.update(position.x, position.y, position.z);
        ip_caveCulling =
                !PortalRendering.isRendering()
                        || (MyGameRenderer.enablePortalCaveCulling
                                && Minecraft.getInstance()
                                        .gameRenderer
                                        .gameRenderState()
                                        .levelRenderState
                                        .cameraRenderState
                                        .smartCull);
        qouteall.imm_ptl.core.render.RenderDiagnostics.sodiumViewport();
    }

    @Inject(
            method = {"isBoxVisible(III)Z", "isBoxVisibleLooser(III)Z"},
            at = @At("HEAD"),
            cancellable = true)
    private void ip_section(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (ip_culler != null
                && ip_culler.canDetermineInvisibleWithWorldCoord(
                        x - 10, y - 10, z - 10, x + 10, y + 10, z + 10)) cir.setReturnValue(false);
    }

    @Inject(method = "isBoxVisibleDirect", at = @At("HEAD"), cancellable = true)
    private void ip_box(
            float x, float y, float z, float radius, CallbackInfoReturnable<Boolean> cir) {
        if (ip_culler != null
                && ip_culler.canDetermineInvisibleWithCameraCoord(
                        x - radius, y - radius, z - radius, x + radius, y + radius, z + radius))
            cir.setReturnValue(false);
    }

    @Inject(method = "getBoxIntersectionDirect", at = @At("HEAD"), cancellable = true)
    private void ip_intersection(
            float x, float y, float z, float radius, CallbackInfoReturnable<Integer> cir) {
        if (ip_culler != null
                && ip_culler.canDetermineInvisibleWithCameraCoord(
                        x - radius, y - radius, z - radius, x + radius, y + radius, z + radius))
            cir.setReturnValue(FrustumIntersection.PLANE_NX);
    }

    @Override
    public boolean ip_allowCaveCulling() {
        return ip_caveCulling;
    }
}

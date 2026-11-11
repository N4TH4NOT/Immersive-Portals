package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface NativeGameRendererAccess {
    @Accessor("lightmap")
    Lightmap ip_lightmap();

    @Accessor("lightmapRenderStateExtractor")
    LightmapRenderStateExtractor ip_lightmapExtractor();

    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform ip_globals();

    @Accessor("resourcePool")
    CrossFrameResourcePool ip_resourcePool();

    @Accessor("fogRenderer")
    net.minecraft.client.renderer.fog.FogRenderer ip_fogRenderer();

    @Mutable
    @Accessor("mainCamera")
    void ip_setNativeCamera(Camera camera);

    @Mutable
    @Accessor("mainRenderTarget")
    void ip_setNativeTarget(RenderTarget target);

    @Mutable
    @Accessor("renderBuffers")
    void ip_setNativeBuffers(RenderBuffers buffers);

    @Invoker("extractWindow")
    void ip_extractWindow();

    @Invoker("extractOptions")
    void ip_extractOptions();

    @Invoker("extractCamera")
    void ip_extractCamera(DeltaTracker delta, float partialTick, float cameraPartialTick);
}

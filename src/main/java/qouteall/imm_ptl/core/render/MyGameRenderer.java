package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import java.util.function.Consumer;

public final class MyGameRenderer {
    public static final Minecraft client = Minecraft.getInstance();
    public static boolean enablePortalCaveCulling = true;
    public static int vanillaTerrainSetupOverride;
    private static RenderTarget renderedTarget;
    private MyGameRenderer() {}
    public static void init() {}
    public static void renderWorldNew(WorldRenderInfo info, Consumer<Runnable> wrapper) {
        WorldRenderInfo.pushRenderInfo(info);
        try { wrapper.accept(() -> renderedTarget = NativeWorldRenderer.render(info)); }
        finally { WorldRenderInfo.popRenderInfo(); }
    }
    public static RenderTarget renderedTarget() { return renderedTarget; }
    public static void resetFogState() {
        // Native scenes restore the owning fog buffer in their finally block.
    }
    public static void updateFogColor() { /* Extracted from the owning world's environment attributes. */ }
    public static void resetDiffuseLighting() { /* Native scenes own and restore their lighting state. */ }
}

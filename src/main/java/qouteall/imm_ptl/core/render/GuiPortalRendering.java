package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuiPortalRendering {
    private static final Map<RenderTarget, WorldRenderInfo> TASKS = new LinkedHashMap<>();
    private static RenderTarget rendering;
    private GuiPortalRendering() {}
    public static RenderTarget getRenderingFrameBuffer() { return rendering; }
    public static boolean isRendering() { return rendering != null; }
    public static void submitNextFrameRendering(WorldRenderInfo info, RenderTarget target) {
        var window = Minecraft.getInstance().getWindow();
        if (target.width != window.getWidth() || target.height != window.getHeight()) target.resize(window.getWidth(), window.getHeight());
        if (TASKS.putIfAbsent(target, info) != null) throw new IllegalArgumentException("A render target already has a pending GUI portal task");
    }
    public static void _onGameRenderEnd() {
        Map<RenderTarget, WorldRenderInfo> frame = new LinkedHashMap<>(TASKS);
        TASKS.clear();
        for (var task : frame.entrySet()) {
            rendering = task.getKey();
            try {
                MyGameRenderer.renderWorldNew(task.getValue(), Runnable::run);
                NativePortalCompositor.drawRectangle(MyGameRenderer.renderedTarget(), rendering, 0,0,rendering.width,rendering.height,false,true);
            } finally { rendering = null; }
        }
    }
    public static void _init() { IPCGlobal.CLIENT_CLEANUP_EVENT.register(TASKS::clear); }
}

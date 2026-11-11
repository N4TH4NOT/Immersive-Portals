package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

public final class SecondaryFrameBuffer implements AutoCloseable {
    public TextureTarget fb;
    public void prepare() { var window = Minecraft.getInstance().getWindow(); prepare(window.getWidth(), window.getHeight()); }
    public void prepare(int width, int height) {
        if (fb == null) fb = new TextureTarget("Immersive portal", width, height, true, GpuFormat.RGBA8_UNORM);
        else if (width != fb.width || height != fb.height) fb.resize(width,height);
    }
    @Override public void close() { if (fb != null) { fb.destroyBuffers(); fb = null; } }
}

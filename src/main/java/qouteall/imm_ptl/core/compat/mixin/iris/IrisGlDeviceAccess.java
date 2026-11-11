package qouteall.imm_ptl.core.compat.mixin.iris;

import com.mojang.blaze3d.opengl.FrameBufferCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface IrisGlDeviceAccess {
    @Accessor("frameBufferCache")
    FrameBufferCache ip_framebuffers();
}

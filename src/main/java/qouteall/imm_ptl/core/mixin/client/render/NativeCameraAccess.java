package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface NativeCameraAccess {
    @Invoker("setupPerspective")
    void ip_setupPerspective(float near, float far, float fov, float width, float height);

    @Accessor("forwards")
    Vector3f ip_forwards();

    @Accessor("up")
    Vector3f ip_up();

    @Accessor("left")
    Vector3f ip_left();

    @Accessor("panoramicForwards")
    Vector3f ip_panoramicForwards();
}

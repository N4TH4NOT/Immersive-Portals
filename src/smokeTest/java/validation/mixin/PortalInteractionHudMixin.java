package validation.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import validation.PortalNativeInteractionSmoke;

/** Isolates native outline pixels without changing the actual HUD visibility option. */
@Mixin(Hud.class)
public abstract class PortalInteractionHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void portalSmoke_hideOutlineCaptureHud(GuiGraphicsExtractor graphics,
            DeltaTracker delta, CallbackInfo callback) {
        if (PortalNativeInteractionSmoke.suppressOutlineHud()) callback.cancel();
    }
}

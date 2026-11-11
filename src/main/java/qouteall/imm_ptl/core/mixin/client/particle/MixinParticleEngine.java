package qouteall.imm_ptl.core.mixin.client.particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.context_management.*;
@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
    @Shadow protected ClientLevel level;
    @Inject(method="extract",at=@At("HEAD"),cancellable=true)
    private void ip_extract(CallbackInfo ci) {
        if(PortalRendering.isRendering() && RenderStates.getRenderedPortalNum()>4)ci.cancel();
    }
    @Override public void ip_setWorld(ClientLevel world) { level=world; }
}

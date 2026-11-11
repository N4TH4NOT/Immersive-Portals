package qouteall.imm_ptl.core.mixin.client.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    @Inject(method="getViewBlockingState",at=@At("HEAD"),cancellable=true)
    private static void ip_wall(Player player,CallbackInfoReturnable<BlockState> cir) {
        if(PortalRendering.isRendering() || ((IEEntity)player).ip_getCollidingPortal()!=null || ClientTeleportationManager.isTeleportingFrequently())cir.setReturnValue(null);
    }
}

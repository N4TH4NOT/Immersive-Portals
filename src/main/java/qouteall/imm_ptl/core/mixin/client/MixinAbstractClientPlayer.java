package qouteall.imm_ptl.core.mixin.client;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.ducks.IEAbstractClientPlayer;
@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayer implements IEAbstractClientPlayer {
    @Override public void ip_setClientLevel(ClientLevel world) {
        ((qouteall.imm_ptl.core.ducks.IEEntity)this).ip_setWorld(world);
    }
}

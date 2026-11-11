package qouteall.imm_ptl.core.mixin.common.container_gui;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;
@Mixin(ContainerOpenersCounter.class)
public abstract class MixinContainerOpenersCounter {
    @Inject(method="getEntitiesWithContainerOpen",at=@At("RETURN"),cancellable=true)
    private void ip_remoteOpeners(Level level,BlockPos pos,CallbackInfoReturnable<List<ContainerUser>> cir) {
        if(level.getServer()==null)return;
        var result=new ArrayList<ContainerUser>(cir.getReturnValue());
        for(var player:level.getServer().getPlayerList().getPlayers())
            if(!player.isSpectator() && player.hasContainerOpen((ContainerOpenersCounter)(Object)this,pos) && !result.contains(player)) result.add(player);
        cir.setReturnValue(result);
    }
}

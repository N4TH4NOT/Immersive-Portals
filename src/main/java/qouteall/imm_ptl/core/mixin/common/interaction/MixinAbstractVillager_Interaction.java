package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.PortalInteractionServer;

@Mixin(AbstractVillager.class)
public abstract class MixinAbstractVillager_Interaction {
    @WrapOperation(method = "stillValid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean ip_keepReachablePortalTrade(Player player, Entity trader, double padding,
            Operation<Boolean> original) {
        return (player.level() == trader.level() && original.call(player, trader, padding))
                || PortalInteractionServer.canKeepEntityContainer(
                        player, trader, player.entityInteractionRange() + padding);
    }
}

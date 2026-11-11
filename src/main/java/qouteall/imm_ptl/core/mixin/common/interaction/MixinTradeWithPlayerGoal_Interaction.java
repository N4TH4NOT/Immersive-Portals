package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.PortalInteractionServer;

@Mixin(TradeWithPlayerGoal.class)
public abstract class MixinTradeWithPlayerGoal_Interaction {
    @WrapOperation(method = "canUse", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/npc/villager/AbstractVillager;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
    private double ip_tradeDistanceThroughOpening(AbstractVillager trader, Entity entity,
            Operation<Double> original) {
        if (entity instanceof Player player
                && PortalInteractionServer.canKeepEntityContainer(player, trader, 4)) return 0;
        return trader.level() == entity.level() ? original.call(trader, entity)
                : Double.POSITIVE_INFINITY;
    }
}

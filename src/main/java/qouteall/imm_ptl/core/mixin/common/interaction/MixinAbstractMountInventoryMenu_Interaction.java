package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.PortalInteractionServer;

@Mixin(AbstractMountInventoryMenu.class)
public abstract class MixinAbstractMountInventoryMenu_Interaction {
    @WrapOperation(method = "stillValid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean ip_keepReachablePortalMountInventory(Player player, Entity mount, double padding,
            Operation<Boolean> original) {
        return (player.level() == mount.level() && original.call(player, mount, padding))
                || PortalInteractionServer.canKeepEntityContainer(
                        player, mount, player.entityInteractionRange() + padding);
    }
}

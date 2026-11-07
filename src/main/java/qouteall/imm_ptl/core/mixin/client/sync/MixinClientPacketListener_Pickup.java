package qouteall.imm_ptl.core.mixin.client.sync;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickup;
import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickupClient;
import qouteall.imm_ptl.core.ducks.IEItemPickupParticle;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener_Pickup {
    @WrapOperation(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;", ordinal = 1))
    private Entity ip_findPickupCollector(ClientLevel world, int id, Operation<Entity> original) {
        Entity result = original.call(world, id);
        if (result != null) return result;
        for (ClientLevel other : ClientWorldLoader.getClientWorlds()) {
            Entity collector = other.getEntity(id);
            if (collector instanceof LivingEntity) return collector;
        }
        return null;
    }

    @WrapOperation(method = "handleTakeItemEntity", at = @At(value = "NEW", target =
            "net/minecraft/client/particle/ItemPickupParticle"))
    private ItemPickupParticle ip_animatePickupThroughPortal(ClientLevel world,
            EntityRenderState state, Entity collector, Vec3 velocity,
            Operation<ItemPickupParticle> original, @Local(ordinal = 0) Entity item) {
        ItemPickupParticle particle = original.call(world, state, collector, velocity);
        var anchor = CrossPortalPickupClient.currentAnchor();
        if (collector instanceof Player player) {
            var portal = CrossPortalPickup.findPortalForPickup(player, world, item.getBoundingBox());
            if (portal != null && (anchor == null || collector.getId() == anchor.collector())) {
                ((IEItemPickupParticle) particle).ip_setPickupPortal(portal);
                return particle;
            }
        }
        if (anchor != null) ((IEItemPickupParticle) particle).ip_setPickupTarget(anchor.position());
        return particle;
    }
}

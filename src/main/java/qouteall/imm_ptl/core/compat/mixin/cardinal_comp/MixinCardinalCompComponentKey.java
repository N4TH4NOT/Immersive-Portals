package qouteall.imm_ptl.core.compat.mixin.cardinal_comp;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

import qouteall.imm_ptl.core.network.PacketRedirection;

/** CCA 8 uses payloads with asynchronous buffer release; preserve the complete send operation. */
@Pseudo
@Mixin(targets = "org.ladysnake.cca.api.v3.component.ComponentKey")
public class MixinCardinalCompComponentKey {
    @WrapMethod(
            method =
                    "syncWith(Lnet/minecraft/server/level/ServerPlayer;Lorg/ladysnake/cca/api/v3/component/ComponentProvider;Lorg/ladysnake/cca/api/v3/component/sync/ComponentPacketWriter;Lorg/ladysnake/cca/api/v3/component/sync/PlayerSyncPredicate;)V")
    private void ip_sync(
            ServerPlayer recipient,
            @Coerce Object provider,
            @Coerce Object writer,
            @Coerce Object predicate,
            Operation<Void> original) {
        ServerLevel world =
                provider instanceof Entity entity && entity.level() instanceof ServerLevel level
                        ? level
                        : provider instanceof BlockEntity block
                                        && block.getLevel() instanceof ServerLevel level
                                ? level
                                : null;
        if (world == null) original.call(recipient, provider, writer, predicate);
        else
            PacketRedirection.withForceRedirect(
                    world, () -> original.call(recipient, provider, writer, predicate));
    }
}

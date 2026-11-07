package qouteall.imm_ptl.core.mixin.client.interaction;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor_Interaction {
    @Shadow private ClientLevel level;

    @ModifyExpressionValue(method = "extractBlockOutline", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;"))
    private HitResult ip_remoteOutline(HitResult original) {
        return BlockManipulationClient.outlineHitForView(level, original);
    }
}

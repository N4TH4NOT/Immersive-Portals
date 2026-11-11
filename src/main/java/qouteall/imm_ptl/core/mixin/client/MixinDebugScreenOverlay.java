package qouteall.imm_ptl.core.mixin.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.*;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {
    @ModifyVariable(method="extractLines",at=@At("HEAD"),argsOnly=true)
    private List<String> ip_debug(List<String> lines,GuiGraphicsExtractor gui,List<String> original,boolean left) {
        if(left)return lines;
        var result=new ArrayList<>(lines);
        result.addAll(IPGlobal.moveDebugTextToTop ? 0 : result.size(),RenderStates.collectDebugText());
        return result;
    }
}

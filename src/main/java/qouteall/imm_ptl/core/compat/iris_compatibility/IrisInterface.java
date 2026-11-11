package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderer;

import org.jetbrains.annotations.Nullable;

public class IrisInterface {
    
    public static class Invoker {
        public boolean isIrisPresent() {
            return false;
        }
        
        public boolean isShaders() {
            return false;
        }
        
        public boolean isRenderingShadowMap() {
            return false;
        }
        
        public Runnable pushContext(Object key,boolean isolated){return ()->{};}
        public void closeContext(Object key){}
        public void finishOffscreen(){}
        public void withCompositeTarget(com.mojang.blaze3d.pipeline.RenderTarget target,java.util.function.Consumer<com.mojang.blaze3d.pipeline.RenderTarget> draw){draw.accept(target);}

        public void reloadPipelines() {}
    
        @Nullable
        public String getShaderpackName() {
            return null;
        }
    }
    
    public static class OnIrisPresent extends Invoker {
        @Override public Runnable pushContext(Object key,boolean isolated){return IrisNativeContext.push(key,isolated);}
        @Override public void closeContext(Object key){IrisNativeContext.close(key);}
        @Override public void finishOffscreen(){IrisNativeContext.finish();}
        @Override public void withCompositeTarget(com.mojang.blaze3d.pipeline.RenderTarget target,java.util.function.Consumer<com.mojang.blaze3d.pipeline.RenderTarget> draw){IrisNativeContext.composite(target,draw);}

        
        @Override
        public boolean isIrisPresent() {
            return true;
        }
        
        @Override
        public boolean isShaders() {
            return Iris.getCurrentPack().isPresent();
        }
        
        @Override
        public boolean isRenderingShadowMap() {
            return ShadowRenderer.ACTIVE;
        }
        
        @Override
        public void reloadPipelines() {
            try {
                Iris.reload();
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Could not reload Iris shader pipelines", error);
            }
        }
    
        @Nullable
        @Override
        public String getShaderpackName() {
            return Iris.getCurrentPackName();
        }
    }
    
    public static Invoker invoker = new Invoker();
}

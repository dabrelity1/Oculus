package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RenderTargetStateListenerMixinsSourceTest {
    @Test
    public void framebufferBindsNotifyPipelineMainFramebufferState() throws Exception {
        String mixin = read("src/main/java/net/oculus/mixin/pipeline/FramebufferStateMixin.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(mixin.contains("@Mixin(Framebuffer.class)"));
        assertTrue(mixin.contains("@Inject(method = \"bindFramebuffer(Z)V\", at = @At(\"RETURN\"))"));
        assertTrue(mixin.contains("PipelineManager.INSTANCE.getPipelineNullable();"));
        assertTrue(mixin.contains("pipeline.getRenderTargetStateListener().setIsMainBound("));
        assertTrue(mixin.contains("this == (Object) minecraft.getFramebuffer()"));
        assertTrue(config.contains("\"pipeline.FramebufferStateMixin\""));
    }

    @Test
    public void shaderGroupRenderNotifiesPostChainScope() throws Exception {
        String mixin = read("src/main/java/net/oculus/mixin/pipeline/ShaderGroupStateMixin.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(mixin.contains("@Mixin(ShaderGroup.class)"));
        assertTrue(mixin.contains("@Inject(method = \"render(F)V\", at = @At(\"HEAD\"))"));
        assertTrue(mixin.contains("pipeline.getRenderTargetStateListener().beginPostChain();"));
        assertTrue(mixin.contains("@Inject(method = \"render(F)V\", at = @At(\"RETURN\"))"));
        assertTrue(mixin.contains("pipeline.getRenderTargetStateListener().endPostChain();"));
        assertTrue(config.contains("\"pipeline.ShaderGroupStateMixin\""));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

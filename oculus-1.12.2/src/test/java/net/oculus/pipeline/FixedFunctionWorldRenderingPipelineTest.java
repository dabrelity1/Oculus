package net.oculus.pipeline;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class FixedFunctionWorldRenderingPipelineTest {
    @Test
    public void keepsVanillaOverlaysEnabledWhenShadersAreDisabled() {
        FixedFunctionWorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();

        assertTrue(pipeline.shouldRenderUnderwaterOverlay());
        assertTrue(pipeline.shouldRenderVignette());
    }

    @Test
    public void beginLevelRenderingRestoresMainFramebufferAndUnbindsProgramLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/FixedFunctionWorldRenderingPipeline.java");

        int method = source.indexOf("public void beginLevelRendering()");
        int framebuffer = source.indexOf("framebuffer.bindFramebuffer(true);", method);
        int unbind = source.indexOf("Program.unbind();", framebuffer);

        assertTrue(method >= 0);
        assertTrue(framebuffer > method);
        assertTrue(unbind > framebuffer);
    }

    @Test
    public void constructorClearsShaderPackRenderLayerOverrides() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/FixedFunctionWorldRenderingPipeline.java");

        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.setBlockStateIds(null);"));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.setRenderLayerOverrides(null);"));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.setEntityIds(null);"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

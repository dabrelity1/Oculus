package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class FramebufferVersionMixinSourceTest {
    @Test
    public void framebufferVersionMixinMirrorsReferenceColorAndDepthVersionBridge() throws Exception {
        String mixin = read("src/main/java/net/oculus/mixin/pipeline/FramebufferVersionMixin.java");
        String accessor = read("src/main/java/net/oculus/rendertarget/MinecraftFramebufferExt.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(mixin.contains("@Mixin(Framebuffer.class)"));
        assertTrue(mixin.contains("implements MinecraftFramebufferExt"));
        assertTrue(mixin.contains("@Inject(method = \"deleteFramebuffer()V\", at = @At(\"HEAD\"))"));
        assertTrue(mixin.contains("if (framebufferTexture > -1)"));
        assertTrue(mixin.contains("oculus$colorBufferVersion++;"));
        assertTrue(mixin.contains("if (depthBuffer > -1)"));
        assertTrue(mixin.contains("oculus$depthBufferVersion++;"));
        assertTrue(mixin.contains("public int oculus$getDepthBufferVersion()"));
        assertTrue(mixin.contains("public int oculus$getColorBufferVersion()"));
        assertTrue(accessor.contains("int oculus$getDepthBufferVersion();"));
        assertTrue(accessor.contains("int oculus$getColorBufferVersion();"));
        assertTrue(config.contains("\"pipeline.FramebufferVersionMixin\""));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

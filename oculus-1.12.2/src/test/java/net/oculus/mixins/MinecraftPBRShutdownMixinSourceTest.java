package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class MinecraftPBRShutdownMixinSourceTest {
    @Test
    public void minecraftShutdownMinecraftAppletClosesPbrTextureManagerBeforeDisplayDestroy() throws Exception {
        String mixin = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/MinecraftPBRShutdownMixin.java")), StandardCharsets.UTF_8);
        String config = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(mixin.contains("@Mixin(Minecraft.class)"));
        assertTrue(mixin.contains("method = \"shutdownMinecraftApplet()V\""));
        assertTrue(mixin.contains("value = \"INVOKE\""));
        assertTrue(mixin.contains("target = \"Lorg/lwjgl/opengl/Display;destroy()V\""));
        assertTrue(mixin.contains("remap = false"));
        assertTrue(mixin.contains("PBRTextureManager.INSTANCE.close();"));
        assertTrue(config.contains("\"pipeline.MinecraftPBRShutdownMixin\""));
    }
}

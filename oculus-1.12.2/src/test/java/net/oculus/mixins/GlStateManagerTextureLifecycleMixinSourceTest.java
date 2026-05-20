package net.oculus.mixins;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class GlStateManagerTextureLifecycleMixinSourceTest {
    @Test
    public void textureLifecycleHookTargetsLowLevelGlStateManagerMethodsAtTail() throws Exception {
        String mixin = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/GlStateManagerTextureLifecycleMixin.java")), StandardCharsets.UTF_8);
        String tracker = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/TextureLifecycleTracker.java")), StandardCharsets.UTF_8);
        String config = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(mixin.contains("@Mixin(GlStateManager.class)"));
        assertTrue(mixin.contains("method = \"glTexImage2D(IIIIIIIILjava/nio/IntBuffer;)V\""));
        assertTrue(mixin.contains("TextureLifecycleTracker.onTexImage2D("));
        assertTrue(mixin.contains("method = \"deleteTexture(I)V\""));
        assertTrue(mixin.contains("at = @At(\"TAIL\")"));
        assertTrue(mixin.contains("TextureLifecycleTracker.onDeleteTexture(textureId);"));
        assertTrue(tracker.contains("TextureInfoCache.INSTANCE.onTexImage2D("));
        assertTrue(tracker.contains("TextureInfoCache.INSTANCE.onDeleteTexture(textureId);"));
        assertTrue(tracker.contains("PBRTextureManager.INSTANCE.onDeleteTexture(textureId);"));
        assertTrue(config.contains("\"pipeline.GlStateManagerTextureLifecycleMixin\""));
        assertFalse(config.contains("TextureUtilPBRDeleteMixin"));
        assertFalse(config.contains("GlStateManagerTextureDeleteMixin"));
    }
}

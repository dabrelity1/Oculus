package net.oculus.mixins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class TextureManagerPBRReloadMixinSourceTest {
    @Test
    public void textureManagerReloadRefreshesTextureFormatAndResetsActivePbrBindings() throws Exception {
        String mixin = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/TextureManagerPBRReloadMixin.java")), StandardCharsets.UTF_8);
        String config = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(mixin.contains("@Mixin(TextureManager.class)"));
        assertTrue(mixin.contains("method = \"onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V\""));
        assertTrue(mixin.contains("at = @At(\"TAIL\")"));
        assertTrue(mixin.contains("Throwable failure = null;"));
        assertTrue(mixin.contains("TextureFormatLoader.reload(resourceManager);"));
        assertTrue(mixin.contains("failure = exception;"));
        assertTrue(mixin.contains("resetActivePbrTextureBindings();"));
        assertTrue(mixin.contains("failure = collectReloadFailure(failure, exception);"));
        assertTrue(mixin.contains("rethrowReloadFailure(failure);"));
        assertTrue(mixin.contains("private static void resetActivePbrTextureBindings()"));
        assertTrue(mixin.contains("WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();"));
        assertTrue(mixin.contains("if (pipeline != null)"));
        assertTrue(mixin.contains("pipeline.resetPbrTextureBindings();"));
        assertTrue(config.contains("\"pipeline.TextureManagerPBRReloadMixin\""));
    }

    @Test
    public void textureManagerReloadSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same reload failure");

        Throwable collected = collectReloadFailure(failure, failure);

        assertSame(failure, collected);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void textureManagerReloadSuppressionKeepsResetFailureContext() throws Exception {
        RuntimeException reloadFailure = new RuntimeException("texture format reload");
        RuntimeException resetFailure = new RuntimeException("pbr reset");

        Throwable collected = collectReloadFailure(reloadFailure, resetFailure);

        assertSame(reloadFailure, collected);
        assertEquals(1, reloadFailure.getSuppressed().length);
        assertSame(resetFailure, reloadFailure.getSuppressed()[0]);
    }

    private static Throwable collectReloadFailure(Throwable failure, Throwable exception) throws Exception {
        Class<?> mixin = Class.forName("net.oculus.mixin.pipeline.TextureManagerPBRReloadMixin");
        Method method = mixin.getDeclaredMethod("collectReloadFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, failure, exception);
    }
}

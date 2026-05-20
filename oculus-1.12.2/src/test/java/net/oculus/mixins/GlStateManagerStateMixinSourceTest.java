package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class GlStateManagerStateMixinSourceTest {
    @Test
    public void bindTextureTrackingOnlyRunsForBaseTextureUnitAndRestoresBinding() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/GlStateManagerStateMixin.java")), StandardCharsets.UTF_8);

        int lockCheck = source.indexOf("oculus$lockBindTextureCallback");
        int suppressedCheck = source.indexOf("OculusRenderSystem.isTextureBindCallbackSuppressed()", lockCheck);
        int activeUnitCheck = source.indexOf("activeTextureUnit != 0", suppressedCheck);
        int notify = source.indexOf(
            "failure = runBindTextureCallback(failure, StateUpdateNotifiers::notifyTextureBindingChanged);",
            activeUnitCheck);
        int pipeline = source.indexOf(
            "failure = runBindTextureCallback(failure, () -> pipeline.onBindTexture(texture));", notify);
        int restore = source.indexOf(
            "failure = runBindTextureCallback(failure, () -> GlStateManager.bindTexture(texture));", pipeline);
        int rethrow = source.indexOf("rethrowBindTextureCallbackFailure(failure);", restore);

        assertTrue(lockCheck >= 0);
        assertTrue(suppressedCheck > lockCheck);
        assertTrue(activeUnitCheck > suppressedCheck);
        assertTrue(notify > activeUnitCheck);
        assertTrue(pipeline > notify);
        assertTrue(restore > pipeline);
        assertTrue(rethrow > restore);
        assertTrue(source.contains("private static Throwable runBindTextureCallback(Throwable failure, Runnable callback)"));
        assertTrue(source.contains("if (exception != failure)"));
        assertTrue(source.contains("failure.addSuppressed(exception);"));
        assertTrue(source.contains("oculus$lockBindTextureCallback = false;"));
    }

    @Test
    public void textureAvailabilityTracks1_12LevelAndBrightnessTextureUnits() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/GlStateManagerStateMixin.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("int lightmapUnit = OpenGlHelper.lightmapTexUnit - OpenGlHelper.defaultTexUnit;"));
        assertTrue(source.contains("int overlayUnit = OpenGlHelper.GL_TEXTURE2 - OpenGlHelper.defaultTexUnit;"));
        assertTrue(source.contains("int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - OpenGlHelper.defaultTexUnit;"));
        assertTrue(source.contains("activeUnit = activeTextureUnit;"));
        assertTrue(source.contains("StateTracker.INSTANCE.albedoSampler = enabled;"));
        assertTrue(source.contains("StateTracker.INSTANCE.lightmapSampler = enabled;"));
        assertTrue(source.contains("StateTracker.INSTANCE.overlaySampler = enabled;"));
        assertTrue(source.contains("pipeline.setInputs(StateTracker.INSTANCE.getInputs());"));
    }

    @Test
    public void syncsPipelineBeforeArrayAndDisplayListDraws() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/GlStateManagerStateMixin.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("@Inject(method = \"glDrawArrays(III)V\", at = @At(\"HEAD\"))"));
        assertTrue(source.contains("@Inject(method = \"callList(I)V\", at = @At(\"HEAD\"))"));
        assertTrue(source.contains("private static void syncPipelineProgram()"));
        assertTrue(source.contains("pipeline.syncProgram();"));
    }
}

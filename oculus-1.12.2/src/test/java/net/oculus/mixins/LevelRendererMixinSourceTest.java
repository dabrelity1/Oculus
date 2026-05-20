package net.oculus.mixins;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class LevelRendererMixinSourceTest {
    @Test
    public void cameraStateRefreshRunsImmediatelyAfterCameraTransform() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/LevelRendererMixin.java");

        assertFalse(source.contains("ActiveRenderInfo;updateRenderInfo"));
        assertTrue(source.contains("PipelineManager.INSTANCE.afterCameraSetup(partialTicks);"));
        assertTrue(source.contains("private void oculus$captureCameraStateAfterCameraTransform"));
        assertTrue(source.contains("target = \"Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V\""));
        assertTrue(source.contains("shift = At.Shift.AFTER"));

        int capture = source.indexOf("private void oculus$captureCameraStateAfterCameraTransform");
        int refresh = source.indexOf("PipelineManager.INSTANCE.afterCameraSetup(partialTicks);", capture);
        int redirect = source.indexOf("private void oculus$setupTerrain");
        int shadows = source.indexOf("pipeline.renderShadows(renderGlobal, entity, (float) partialTicks);", redirect);
        int staleRefresh = source.indexOf("PipelineManager.INSTANCE.afterCameraSetup((float) partialTicks);", redirect);
        assertTrue(capture >= 0);
        assertTrue(refresh > capture);
        assertTrue(redirect >= 0);
        assertTrue(redirect > refresh);
        assertTrue(shadows > redirect);
        assertTrue(staleRefresh < 0);
    }

    @Test
    public void shaderHandPathSuppressesLateVanillaDepthClearAndDuplicateHand() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/LevelRendererMixin.java");

        assertTrue(source.contains("private void oculus$skipLateShaderHandDepthClear(int mask)"));
        assertTrue(source.contains(
            "target = \"Lnet/minecraft/client/renderer/GlStateManager;clear(I)V\",\n"
                + "            ordinal = 1"));
        assertTrue(source.contains("private void oculus$skipLateVanillaHand(EntityRenderer renderer, float partialTicks, int pass)"));
        assertTrue(source.contains("private boolean oculus$usesEarlyShaderHand()"));
        assertTrue(source.contains("pipeline instanceof ShaderWorldRenderingPipeline"));
        assertTrue(source.contains("!pipeline.isRenderingShadowPass()"));

        int clearRedirect = source.indexOf("private void oculus$skipLateShaderHandDepthClear");
        int guardedClear = source.indexOf("if (!oculus$usesEarlyShaderHand())", clearRedirect);
        int clearCall = source.indexOf("GlStateManager.clear(mask);", guardedClear);
        int handRedirect = source.indexOf("private void oculus$skipLateVanillaHand");
        int guardedHand = source.indexOf("if (!oculus$usesEarlyShaderHand())", handRedirect);
        int handCall = source.indexOf("renderHand(partialTicks, pass);", guardedHand);

        assertTrue(clearRedirect >= 0);
        assertTrue(guardedClear > clearRedirect);
        assertTrue(clearCall > guardedClear);
        assertTrue(handRedirect > clearCall);
        assertTrue(guardedHand > handRedirect);
        assertTrue(handCall > guardedHand);
    }

    @Test
    public void earlyShaderHandUsesSolidPhaseBecause112PassIsNotHandOpacity() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/LevelRendererMixin.java");
        String beginHand = methodBody(source, "private void oculus$beginHand");

        assertTrue(beginHand.contains("setPhase(WorldRenderingPhase.HAND_SOLID);"));
        assertFalse("The 1.12 renderHand pass argument is the world render pass, not a solid/translucent hand selector",
            beginHand.contains("WorldRenderingPhase.HAND_TRANSLUCENT"));
        assertFalse(beginHand.contains("pass == 0 ?"));
    }

    @Test
    public void runtimeValidationScreenshotCapturesAfterWorldFinalizeBeforeGuiOverlay() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/LevelRendererMixin.java");
        String endWorld = methodBody(source, "private void oculus$endWorld");

        int finalize = endWorld.indexOf("pipeline.finalizeLevelRendering();");
        int capture = endWorld.indexOf("OculusRuntimeValidation.captureWorldScreenshotAfterRender(mc);", finalize);

        assertTrue(finalize >= 0);
        assertTrue(capture > finalize);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Unable to read method body for " + signature);
    }
}

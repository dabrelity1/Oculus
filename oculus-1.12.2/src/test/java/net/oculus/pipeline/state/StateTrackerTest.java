package net.oculus.pipeline.state;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.oculus.pipeline.InputAvailability;
import org.junit.Test;

public class StateTrackerTest {
    @Test
    public void getInputsReflectsTrackedTextureLightmapAndOverlaySamplers() {
        StateTracker.INSTANCE.albedoSampler = true;
        StateTracker.INSTANCE.lightmapSampler = true;
        StateTracker.INSTANCE.overlaySampler = true;

        try {
            InputAvailability inputs = StateTracker.INSTANCE.getInputs();

            assertTrue(inputs.texture);
            assertTrue(inputs.lightmap);
            assertTrue(inputs.overlay);
        } finally {
            StateTracker.INSTANCE.albedoSampler = false;
            StateTracker.INSTANCE.lightmapSampler = false;
            StateTracker.INSTANCE.overlaySampler = false;
        }
    }

    @Test
    public void getInputsKeepsOverlayFalseWhenBrightnessOverlayUnitIsNotEnabled() {
        StateTracker.INSTANCE.albedoSampler = true;
        StateTracker.INSTANCE.lightmapSampler = true;
        StateTracker.INSTANCE.overlaySampler = false;

        try {
            InputAvailability inputs = StateTracker.INSTANCE.getInputs();

            assertTrue(inputs.texture);
            assertTrue(inputs.lightmap);
            assertFalse(inputs.overlay);
        } finally {
            StateTracker.INSTANCE.albedoSampler = false;
            StateTracker.INSTANCE.lightmapSampler = false;
            StateTracker.INSTANCE.overlaySampler = false;
        }
    }

    @Test
    public void refreshFromGlStateReadsAllShaderPackSamplerUnitsAndRestoresActiveTexture() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/state/StateTracker.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("public void refreshFromGlState()"));
        assertTrue(source.contains("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);"));
        assertTrue(source.contains("OculusRenderSystem.setActiveTextureUnit(OpenGlHelper.defaultTexUnit);"));
        assertTrue(source.contains("albedoSampler = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);"));
        assertTrue(source.contains("OculusRenderSystem.setActiveTextureUnit(OpenGlHelper.lightmapTexUnit);"));
        assertTrue(source.contains("lightmapSampler = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);"));
        assertTrue(source.contains("OculusRenderSystem.setActiveTextureUnit(OpenGlHelper.GL_TEXTURE2);"));
        assertTrue(source.contains("overlaySampler = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);"));
    }
}

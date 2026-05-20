package net.oculus.pipeline.state;

import net.minecraft.client.renderer.OpenGlHelper;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.pipeline.InputAvailability;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class StateTracker {
    public static final StateTracker INSTANCE = new StateTracker();

    public boolean albedoSampler;
    public boolean lightmapSampler;
    public boolean overlaySampler;

    private StateTracker() {
    }

    public InputAvailability getInputs() {
        return new InputAvailability(albedoSampler, lightmapSampler, overlaySampler);
    }

    public void refreshFromGlState() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        try {
            OculusRenderSystem.setActiveTextureUnit(OpenGlHelper.defaultTexUnit);
            albedoSampler = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

            OculusRenderSystem.setActiveTextureUnit(OpenGlHelper.lightmapTexUnit);
            lightmapSampler = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

            OculusRenderSystem.setActiveTextureUnit(OpenGlHelper.GL_TEXTURE2);
            overlaySampler = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        } finally {
            OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
        }
    }
}

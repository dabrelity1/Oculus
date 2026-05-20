package net.oculus.gl.blending;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class AlphaTestOverride {
    public static final AlphaTestOverride OFF = new AlphaTestOverride(null);

    private static boolean saved;
    private static boolean savedEnabled;
    private static int savedFunction;
    private static float savedReference;

    private final AlphaTest alphaTest;

    public AlphaTestOverride(AlphaTest alphaTest) {
        this.alphaTest = alphaTest;
    }

    public boolean isDisabled() {
        return alphaTest == null;
    }

    public AlphaTest getAlphaTest() {
        return alphaTest;
    }

    public void apply() {
        saveState();

        if (alphaTest == null) {
            GlStateManager.disableAlpha();
            return;
        }

        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(alphaTest.getFunction().getGlId(), alphaTest.getReference());
    }

    public static void restore() {
        if (!saved) {
            return;
        }

        if (savedEnabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        GlStateManager.alphaFunc(savedFunction, savedReference);
        saved = false;
    }

    private static void saveState() {
        if (saved) {
            return;
        }

        savedEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        savedFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        savedReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        saved = true;
    }
}

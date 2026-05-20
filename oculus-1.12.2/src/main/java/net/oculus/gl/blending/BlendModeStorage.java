package net.oculus.gl.blending;

import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.OculusRenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class BlendModeStorage {
    private static boolean originalBlendEnable;
    private static BlendMode originalBlend;
    private static boolean blendLocked;

    private BlendModeStorage() {
    }

    public static boolean isBlendLocked() {
        return blendLocked;
    }

    public static void overrideBlend(BlendMode override) {
        if (!blendLocked) {
            saveBlendState();
        }

        blendLocked = false;

        if (override == null) {
            GlStateManager.disableBlend();
        } else {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                override.getSrcRgb(),
                override.getDstRgb(),
                override.getSrcAlpha(),
                override.getDstAlpha()
            );
        }

        blendLocked = true;
    }

    public static void overrideBufferBlend(int index, BlendMode override) {
        if (!blendLocked) {
            saveBlendState();
        }

        if (override == null) {
            OculusRenderSystem.disableBufferBlend(index);
        } else {
            OculusRenderSystem.enableBufferBlend(index);
            OculusRenderSystem.blendFuncSeparatei(
                index,
                override.getSrcRgb(),
                override.getDstRgb(),
                override.getSrcAlpha(),
                override.getDstAlpha()
            );
        }

        blendLocked = true;
    }

    public static void deferBlendModeToggle(boolean enabled) {
        originalBlendEnable = enabled;
    }

    public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        originalBlend = new BlendMode(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void restoreBlend() {
        if (!blendLocked || originalBlend == null) {
            return;
        }

        blendLocked = false;

        if (originalBlendEnable) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }

        GlStateManager.tryBlendFuncSeparate(
            originalBlend.getSrcRgb(),
            originalBlend.getDstRgb(),
            originalBlend.getSrcAlpha(),
            originalBlend.getDstAlpha()
        );
        originalBlend = null;
    }

    private static void saveBlendState() {
        originalBlendEnable = GL11.glIsEnabled(GL11.GL_BLEND);
        originalBlend = new BlendMode(
            GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
            GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
            GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        );
    }
}

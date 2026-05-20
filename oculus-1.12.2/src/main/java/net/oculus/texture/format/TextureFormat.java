package net.oculus.texture.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.texture.mipmap.CustomMipmapGenerator;
import net.oculus.texture.pbr.PBRType;
import org.lwjgl.opengl.GL11;

/**
 * Texture-pack format metadata loaded from {@code optifine/texture.properties}.
 */
public interface TextureFormat {
    String getName();

    String getVersion();

    default List<String> getDefines() {
        List<String> defines = new ArrayList<>();

        String defineName = getName().toUpperCase(Locale.ROOT).replaceAll("-", "_");
        String define = "MC_TEXTURE_FORMAT_" + defineName;
        defines.add(define);

        String version = getVersion();
        if (version != null) {
            String defineVersion = version.replaceAll("[.-]", "_");
            defines.add(define + "_" + defineVersion);
        }

        return defines;
    }

    boolean canInterpolateValues(PBRType pbrType);

    default CustomMipmapGenerator getMipmapGenerator(PBRType pbrType) {
        return null;
    }

    default void setupTextureParameters(PBRType pbrType, int textureId) {
        if (textureId <= 0 || canInterpolateValues(pbrType)) {
            return;
        }

        OculusRenderSystem.runWithoutTextureBindCallback(() -> {
            int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            Throwable setupFailure = null;
            try {
                GlStateManager.bindTexture(textureId);
                int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                boolean mipmap = hasMipmappedMinFilter(minFilter);
                GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER,
                    mipmap ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            } catch (RuntimeException | Error exception) {
                setupFailure = exception;
                throw exception;
            } finally {
                restorePreviousTextureBinding(previousTextureBinding, setupFailure);
            }
        });
    }

    static boolean hasMipmappedMinFilter(int minFilter) {
        return minFilter >= GL11.GL_NEAREST_MIPMAP_NEAREST && minFilter <= GL11.GL_LINEAR_MIPMAP_LINEAR;
    }

    static void restorePreviousTextureBinding(int previousTextureBinding, Throwable setupFailure) {
        try {
            GlStateManager.bindTexture(previousTextureBinding);
        } catch (RuntimeException | Error restoreFailure) {
            if (setupFailure != null) {
                suppressRestoreFailure(setupFailure, restoreFailure);
                return;
            }
            throw restoreFailure;
        }
    }

    static void suppressRestoreFailure(Throwable setupFailure, Throwable restoreFailure) {
        if (restoreFailure != null && restoreFailure != setupFailure) {
            setupFailure.addSuppressed(restoreFailure);
        }
    }

    interface Factory {
        TextureFormat createFormat(String name, String version);
    }
}

package net.oculus.texture;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import net.minecraft.client.renderer.GlStateManager;
import net.oculus.Oculus;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class FallbackTextures {
    private static int whiteTexture;

    private FallbackTextures() {
    }

    public static int getWhiteTexture() {
        if (whiteTexture == 0) {
            whiteTexture = createSingleColorTexture(0xFFFFFFFF);
        }
        return whiteTexture;
    }

    public static void destroy() {
        int texture = whiteTexture;
        whiteTexture = 0;
        deleteTexture(texture, "white fallback texture");
    }

    static byte[] rgbaBytes(int rgba) {
        return new byte[] {
            (byte) ((rgba >>> 24) & 0xFF),
            (byte) ((rgba >>> 16) & 0xFF),
            (byte) ((rgba >>> 8) & 0xFF),
            (byte) (rgba & 0xFF)
        };
    }

    private static int createSingleColorTexture(int rgba) {
        byte[] pixel = rgbaBytes(rgba);
        ByteBuffer buffer = BufferUtils.createByteBuffer(pixel.length);
        buffer.put(pixel);
        ((Buffer) buffer).flip();

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to allocate white fallback texture");
        }

        boolean success = false;
        Throwable setupFailure = null;
        Throwable restoreFailure = null;
        try {
            GlStateManager.bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                1,
                1,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer);
            TextureLifecycleTracker.onTexImage2D(texture, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1);
            success = true;
            return texture;
        } catch (RuntimeException | Error exception) {
            setupFailure = exception;
            throw exception;
        } finally {
            try {
                restorePreviousTextureBinding(previousTexture, setupFailure);
            } catch (RuntimeException | Error exception) {
                restoreFailure = exception;
                throw exception;
            } finally {
                if (!success || restoreFailure != null) {
                    deleteTexture(texture, "white fallback texture");
                }
            }
        }
    }

    private static void restorePreviousTextureBinding(int previousTexture, Throwable setupFailure) {
        try {
            GlStateManager.bindTexture(previousTexture);
        } catch (RuntimeException | Error restoreFailure) {
            if (setupFailure != null) {
                suppressRestoreFailure(setupFailure, restoreFailure);
                return;
            }
            throw restoreFailure;
        }
    }

    private static void suppressRestoreFailure(Throwable setupFailure, Throwable restoreFailure) {
        if (restoreFailure != null && restoreFailure != setupFailure) {
            setupFailure.addSuppressed(restoreFailure);
        }
    }

    private static void deleteTexture(int textureId, String description) {
        if (textureId <= 0) {
            return;
        }

        try {
            GL11.glDeleteTextures(textureId);
        } catch (RuntimeException | Error exception) {
            Oculus.LOGGER.debug("Failed to delete {}", description, exception);
        } finally {
            TextureLifecycleTracker.onDeleteTexture(textureId);
        }
    }
}

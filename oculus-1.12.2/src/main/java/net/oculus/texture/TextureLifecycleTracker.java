package net.oculus.texture;

import java.nio.IntBuffer;

import javax.annotation.Nullable;

import net.oculus.Oculus;
import net.oculus.texture.pbr.PBRTextureManager;
import org.lwjgl.opengl.GL11;

public final class TextureLifecycleTracker {
    private TextureLifecycleTracker() {
    }

    public static void onTexImage2D(int target,
                                    int level,
                                    int internalFormat,
                                    int width,
                                    int height,
                                    int border,
                                    int format,
                                    int type,
                                    @Nullable IntBuffer pixels) {
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public static void onTexImage2D(int textureId, int target, int level, int internalFormat, int width, int height) {
        TextureInfoCache.INSTANCE.onTexImage2D(textureId, target, level, internalFormat, width, height);
    }

    public static void onTexImage3D(int textureId, int target, int level, int internalFormat,
                                    int width, int height, int depth) {
        TextureInfoCache.INSTANCE.onTexImage3D(textureId, target, level, internalFormat, width, height, depth);
    }

    public static void onCopyTexImage2D(int target,
                                        int level,
                                        int internalFormat,
                                        int width,
                                        int height,
                                        int border) {
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalFormat, width, height, border, 0, 0, null);
    }

    public static void onTextureUtilAllocation2D(int textureId, int width, int height) {
        TextureInfoCache.INSTANCE.onTexImage2D(textureId, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height);
    }

    public static void onDeleteTexture(int textureId) {
        if (textureId <= 0) {
            return;
        }

        Throwable failure = null;
        failure = runDeleteNotification(failure, () -> TextureTracker.INSTANCE.onDeleteTexture(textureId));
        failure = runDeleteNotification(failure, () -> TextureInfoCache.INSTANCE.onDeleteTexture(textureId));
        failure = runDeleteNotification(failure, () -> PBRTextureManager.INSTANCE.onDeleteTexture(textureId));

        if (failure != null) {
            Oculus.LOGGER.debug("Failed to update texture lifecycle state for deleted texture {}", textureId, failure);
        }
    }

    private static Throwable runDeleteNotification(Throwable failure, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                suppressNotificationFailure(failure, exception);
                return failure;
            }
            return exception;
        }
        return failure;
    }

    private static void suppressNotificationFailure(Throwable failure, Throwable exception) {
        if (exception != null && exception != failure) {
            failure.addSuppressed(exception);
        }
    }
}

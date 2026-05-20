package net.oculus.texture;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

public final class TextureInfoCache {
    public static final TextureInfoCache INSTANCE = new TextureInfoCache();

    private final Map<Integer, TextureInfo> cache = new HashMap<>();

    private TextureInfoCache() {
    }

    public synchronized TextureInfo getInfo(int id) {
        TextureInfo info = cache.get(id);
        if (info == null) {
            info = new TextureInfo(id);
            cache.put(id, info);
        }
        return info;
    }

    public void onTexImage2D(int target,
                             int level,
                             int internalFormat,
                             int width,
                             int height,
                             int border,
                             int format,
                             int type,
                             @Nullable IntBuffer pixels) {
        if (level == 0 && isTexImage2DTarget(target)) {
            int id = getBoundTextureId(target);
            if (id > 0) {
                onTextureImage(id, target, internalFormat, width, height, 1);
            }
        }
    }

    synchronized void onTexImage2D(int id, int internalFormat, int width, int height) {
        onTextureImage(id, GL11.GL_TEXTURE_2D, internalFormat, width, height, 1);
    }

    public void onTexImage2D(int id, int target, int level, int internalFormat, int width, int height) {
        if (level == 0) {
            onTextureImage(id, target, internalFormat, width, height, 1);
        }
    }

    public void onTexImage3D(int id, int target, int level, int internalFormat, int width, int height, int depth) {
        if (level == 0) {
            onTextureImage(id, target, internalFormat, width, height, depth);
        }
    }

    public synchronized void onDeleteTexture(int id) {
        cache.remove(id);
    }

    synchronized void clear() {
        cache.clear();
    }

    synchronized boolean hasCachedInfo(int id) {
        return cache.containsKey(id);
    }

    private synchronized void onTextureImage(int id, int target, int internalFormat, int width, int height, int depth) {
        if (id <= 0) {
            return;
        }

        TextureInfo info = getInfo(id);
        info.target = normalizeTarget(target);
        info.internalFormat = internalFormat;
        info.width = width;
        info.height = height;
        info.depth = Math.max(1, depth);
    }

    private static int getBoundTextureId(int target) {
        return GL11.glGetInteger(bindingParameter(normalizeTarget(target)));
    }

    private static boolean isTexImage2DTarget(int target) {
        return target == GL11.GL_TEXTURE_2D
            || target == GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X
            || target == GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X
            || target == GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y
            || target == GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
            || target == GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z
            || target == GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z;
    }

    private static int normalizeTarget(int target) {
        return target == GL12.GL_TEXTURE_3D || isTexImage2DTarget(target) ? target : GL11.GL_TEXTURE_2D;
    }

    private static int bindingTarget(int target) {
        switch (target) {
            case GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X:
            case GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X:
            case GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y:
            case GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y:
            case GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z:
            case GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z:
                return GL13.GL_TEXTURE_CUBE_MAP;
            default:
                return target;
        }
    }

    private static int bindingParameter(int target) {
        int bindingTarget = bindingTarget(target);
        if (bindingTarget == GL12.GL_TEXTURE_3D) {
            return GL12.GL_TEXTURE_BINDING_3D;
        }
        if (bindingTarget == GL13.GL_TEXTURE_CUBE_MAP) {
            return GL13.GL_TEXTURE_BINDING_CUBE_MAP;
        }
        return GL11.GL_TEXTURE_BINDING_2D;
    }

    public static final class TextureInfo {
        private final int id;
        private int target = GL11.GL_TEXTURE_2D;
        private int internalFormat = -1;
        private int width = -1;
        private int height = -1;
        private int depth = -1;

        private TextureInfo(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getInternalFormat() {
            if (internalFormat == -1) {
                internalFormat = fetchLevelParameter(GL11.GL_TEXTURE_INTERNAL_FORMAT);
            }
            return internalFormat;
        }

        public int getWidth() {
            if (width == -1) {
                width = fetchLevelParameter(GL11.GL_TEXTURE_WIDTH);
            }
            return width;
        }

        public int getHeight() {
            if (height == -1) {
                height = fetchLevelParameter(GL11.GL_TEXTURE_HEIGHT);
            }
            return height;
        }

        public int getDepth() {
            if (depth == -1) {
                depth = Math.max(1, fetchLevelParameter(GL12.GL_TEXTURE_DEPTH));
            }
            return depth;
        }

        private int fetchLevelParameter(int parameterName) {
            int bindingTarget = bindingTarget(target);
            int previousTextureBinding = GL11.glGetInteger(bindingParameter(target));
            Throwable queryFailure = null;
            try {
                bindTexture(bindingTarget, id);
                return GL11.glGetTexLevelParameteri(target, 0, parameterName);
            } catch (RuntimeException | Error exception) {
                queryFailure = exception;
                throw exception;
            } finally {
                restorePreviousTextureBinding(bindingTarget, previousTextureBinding, queryFailure);
            }
        }

        private static void restorePreviousTextureBinding(int bindingTarget, int previousTextureBinding,
                                                          Throwable queryFailure) {
            try {
                bindTexture(bindingTarget, previousTextureBinding);
            } catch (RuntimeException | Error restoreFailure) {
                if (queryFailure != null) {
                    suppressRestoreFailure(queryFailure, restoreFailure);
                    return;
                }
                throw restoreFailure;
            }
        }

        private static void suppressRestoreFailure(Throwable queryFailure, Throwable restoreFailure) {
            if (restoreFailure != null && restoreFailure != queryFailure) {
                queryFailure.addSuppressed(restoreFailure);
            }
        }

        private static void bindTexture(int target, int texture) {
            if (target == GL11.GL_TEXTURE_2D) {
                GlStateManager.bindTexture(texture);
            } else {
                GL11.glBindTexture(target, texture);
            }
        }
    }
}

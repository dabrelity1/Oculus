package net.oculus.pipeline.texture;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import net.oculus.Oculus;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.values.OptionValues;
import net.oculus.shaderpack.texture.CustomImageData;
import net.oculus.texture.TextureLifecycleTracker;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Allocates shader-pack declared custom image textures and binds each one both as an
 * image uniform and as its paired sampler uniform.
 */
public final class CustomImageManager {
    private final Map<String, CustomImageData> imageData;
    private final OptionValues optionValues;
    private final Map<String, CustomImageTexture> textures = new LinkedHashMap<>();
    private int framebufferWidth = -1;
    private int framebufferHeight = -1;

    public CustomImageManager(ShaderProperties properties, OptionValues optionValues) {
        this.imageData = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties.getCustomImages());
        this.optionValues = optionValues;
    }

    public void initializeOrResize(int width, int height) {
        if (imageData.isEmpty()) {
            return;
        }

        int resolvedWidth = Math.max(1, width);
        int resolvedHeight = Math.max(1, height);
        if (!textures.isEmpty() && framebufferWidth == resolvedWidth && framebufferHeight == resolvedHeight) {
            return;
        }

        Map<String, CustomImageTexture> newTextures = new LinkedHashMap<>();
        try {
            for (CustomImageData data : imageData.values()) {
                Dimensions dimensions = resolveDimensions(data, resolvedWidth, resolvedHeight);
                CustomImageTexture texture = allocateTexture(data, dimensions);
                newTextures.put(data.getImageName(), texture);
            }
        } catch (RuntimeException | Error exception) {
            destroyTextureMap(newTextures, false);
            throw exception;
        }

        replaceTextures(newTextures, resolvedWidth, resolvedHeight);
    }

    public void clearNewFrameImages() {
        Throwable failure = null;
        for (CustomImageTexture texture : textures.values()) {
            if (texture.data.shouldClearOnNewFrame()) {
                try {
                    clearTexture(texture);
                } catch (RuntimeException | Error exception) {
                    failure = collectFailure(failure, exception);
                }
            }
        }
        rethrowFailure(failure);
    }

    public void applyToProgram(ProgramBuilder builder) {
        if (builder == null || textures.isEmpty()) {
            return;
        }

        for (CustomImageTexture texture : textures.values()) {
            CustomImageData data = texture.data;
            if (builder.hasImage(data.getImageName())) {
                builder.addTextureImage(() -> getTextureId(data.getImageName()), data.getInternalFormat(),
                    data.getImageName());
            }
            builder.overrideSamplerBinding(data.getSamplerName(), texture.binding);
        }
    }

    private TextureBinding createBinding(CustomImageData data) {
        return data.isThreeDimensional()
            ? TextureBinding.texture3D(() -> getTextureId(data.getImageName()))
            : TextureBinding.texture2D(() -> getTextureId(data.getImageName()));
    }

    private int getTextureId(String imageName) {
        CustomImageTexture texture = textures.get(imageName);
        return texture != null ? texture.textureId : 0;
    }

    public void destroy() {
        try {
            destroyTextures();
        } finally {
            framebufferWidth = -1;
            framebufferHeight = -1;
        }
    }

    private CustomImageTexture allocateTexture(CustomImageData data, Dimensions dimensions) {
        int target = data.isThreeDimensional() ? GL12.GL_TEXTURE_3D : GL11.GL_TEXTURE_2D;
        int textureId = GL11.glGenTextures();
        if (textureId <= 0) {
            throw new IllegalStateException("Failed to allocate custom image texture " + data.getImageName());
        }

        int previousActiveTexture = GL13.GL_TEXTURE0;
        boolean previousActiveTextureCaptured = false;
        int previousTexture = 0;
        boolean previousTextureCaptured = false;
        boolean success = false;
        Throwable setupFailure = null;
        try {
            previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            previousActiveTextureCaptured = true;
            OculusRenderSystem.restoreDefaultActiveTexture();
            previousTexture = getBoundTexture(target);
            previousTextureCaptured = true;
            bindTexture(target, textureId);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (target == GL12.GL_TEXTURE_3D) {
                GL11.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
                GL12.glTexImage3D(
                    target,
                    0,
                    data.getInternalFormat().getGlFormat(),
                    dimensions.width,
                    dimensions.height,
                    dimensions.depth,
                    0,
                    data.getPixelFormat().getGlFormat(),
                    data.getPixelType().getGlFormat(),
                    (ByteBuffer) null);
                TextureLifecycleTracker.onTexImage3D(textureId, target, 0, data.getInternalFormat().getGlFormat(),
                    dimensions.width, dimensions.height, dimensions.depth);
            } else {
                GL11.glTexImage2D(
                    target,
                    0,
                    data.getInternalFormat().getGlFormat(),
                    dimensions.width,
                    dimensions.height,
                    0,
                    data.getPixelFormat().getGlFormat(),
                    data.getPixelType().getGlFormat(),
                    (ByteBuffer) null);
                TextureLifecycleTracker.onTexImage2D(textureId, target, 0, data.getInternalFormat().getGlFormat(),
                    dimensions.width, dimensions.height);
            }
            CustomImageTexture texture = new CustomImageTexture(data, dimensions, textureId, target, createBinding(data));
            clearTexture(texture);
            success = true;
            return texture;
        } catch (RuntimeException | Error exception) {
            setupFailure = exception;
            throw exception;
        } finally {
            Throwable restoreFailure = null;
            restoreFailure = restoreTextureBinding(target, previousTexture, previousTextureCaptured, setupFailure,
                restoreFailure);
            restoreFailure = restoreActiveTexture(previousActiveTexture, previousActiveTextureCaptured, setupFailure,
                restoreFailure);
            boolean cleanupTexture = !success || restoreFailure != null;
            if (cleanupTexture && textureId > 0) {
                deleteTexture(textureId, data.getImageName());
            }
            rethrowFailure(restoreFailure);
        }
    }

    private void clearTexture(CustomImageTexture texture) {
        CustomImageData data = texture.data;
        ByteBuffer zero = BufferUtils.createByteBuffer(16);

        if (OculusRenderSystem.clearTexImage(texture.textureId, 0, data.getPixelFormat().getGlFormat(),
            data.getPixelType().getGlFormat(), zero)) {
            return;
        }

        clearTextureFallback(texture);
    }

    private void clearTextureFallback(CustomImageTexture texture) {
        CustomImageData data = texture.data;
        int bytesPerPixel = bytesPerPixel(data.getPixelFormat(), data.getPixelType());
        int sliceBytes = safeBufferSize((long) texture.dimensions.width * texture.dimensions.height * bytesPerPixel,
            data.getImageName());

        ByteBuffer zeros = BufferUtils.createByteBuffer(sliceBytes);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int previousTexture = 0;
        boolean previousTextureCaptured = false;
        boolean unpackAlignmentChanged = false;
        Throwable clearFailure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            previousTexture = getBoundTexture(texture.target);
            previousTextureCaptured = true;
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            unpackAlignmentChanged = true;
            bindTexture(texture.target, texture.textureId);
            if (texture.target == GL12.GL_TEXTURE_3D) {
                for (int z = 0; z < texture.dimensions.depth; z++) {
                    zeros.clear();
                    GL12.glTexSubImage3D(
                        GL12.GL_TEXTURE_3D,
                        0,
                        0,
                        0,
                        z,
                        texture.dimensions.width,
                        texture.dimensions.height,
                        1,
                        data.getPixelFormat().getGlFormat(),
                        data.getPixelType().getGlFormat(),
                        zeros);
                }
            } else {
                GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    texture.dimensions.width,
                    texture.dimensions.height,
                    data.getPixelFormat().getGlFormat(),
                    data.getPixelType().getGlFormat(),
                    zeros);
            }
        } catch (RuntimeException | Error exception) {
            clearFailure = exception;
            throw exception;
        } finally {
            Throwable restoreFailure = null;
            restoreFailure = restoreTextureBinding(texture.target, previousTexture, previousTextureCaptured, clearFailure,
                restoreFailure);
            restoreFailure = restoreUnpackAlignment(previousUnpackAlignment, unpackAlignmentChanged, clearFailure,
                restoreFailure);
            restoreFailure = restoreActiveTexture(previousActiveTexture, true, clearFailure, restoreFailure);
            rethrowFailure(restoreFailure);
        }
    }

    private static Throwable restoreTextureBinding(int target, int previousTexture, boolean previousTextureCaptured,
                                                   Throwable primaryFailure, Throwable restoreFailure) {
        if (!previousTextureCaptured) {
            return restoreFailure;
        }
        try {
            bindTexture(target, previousTexture);
        } catch (RuntimeException | Error exception) {
            return collectRestoreFailure(primaryFailure, restoreFailure, exception);
        }
        return restoreFailure;
    }

    private static Throwable restoreUnpackAlignment(int previousUnpackAlignment, boolean unpackAlignmentChanged,
                                                    Throwable primaryFailure, Throwable restoreFailure) {
        if (!unpackAlignmentChanged) {
            return restoreFailure;
        }
        try {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment);
        } catch (RuntimeException | Error exception) {
            return collectRestoreFailure(primaryFailure, restoreFailure, exception);
        }
        return restoreFailure;
    }

    private static Throwable restoreActiveTexture(int previousActiveTexture, boolean previousActiveTextureCaptured,
                                                  Throwable primaryFailure, Throwable restoreFailure) {
        if (!previousActiveTextureCaptured) {
            return restoreFailure;
        }
        try {
            OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
        } catch (RuntimeException | Error exception) {
            return collectRestoreFailure(primaryFailure, restoreFailure, exception);
        }
        return restoreFailure;
    }

    private static Throwable collectRestoreFailure(Throwable primaryFailure, Throwable restoreFailure,
                                                   Throwable exception) {
        if (primaryFailure != null) {
            suppressFailure(primaryFailure, exception);
            return restoreFailure;
        }
        return collectFailure(restoreFailure, exception);
    }

    private static int getBoundTexture(int target) {
        if (target == GL12.GL_TEXTURE_3D) {
            return GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        }
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static void bindTexture(int target, int textureId) {
        if (target == GL11.GL_TEXTURE_2D) {
            GlStateManager.bindTexture(textureId);
        } else {
            GL11.glBindTexture(target, textureId);
        }
    }

    private Dimensions resolveDimensions(CustomImageData data, int baseWidth, int baseHeight) {
        Integer width = resolveDimension(data.getWidthExpression(), data.isRelative(), baseWidth, data.getImageName());
        Integer height = resolveDimension(data.getHeightExpression(), data.isRelative(), baseHeight, data.getImageName());
        Integer depth = data.isThreeDimensional()
            ? resolveAbsoluteDimension(data.getDepthExpression(), data.getImageName())
            : 1;

        return new Dimensions(width, height, depth);
    }

    private Integer resolveDimension(String expression, boolean relative, int base, String imageName) {
        Float value = resolveNumericExpression(expression);
        if (value == null) {
            throw new IllegalStateException("Unable to resolve custom image dimension '"
                + expression + "' for " + imageName);
        }

        if (relative) {
            return Math.max(1, (int) Math.ceil(base * value));
        }

        return Math.max(1, Math.round(value));
    }

    private Integer resolveAbsoluteDimension(String expression, String imageName) {
        Float value = resolveNumericExpression(expression);
        if (value == null) {
            throw new IllegalStateException("Unable to resolve custom image depth '"
                + expression + "' for " + imageName);
        }
        return Math.max(1, Math.round(value));
    }

    private Float resolveNumericExpression(String expression) {
        if (expression == null) {
            return null;
        }

        Float literal = parseFloat(expression);
        if (literal != null) {
            return literal;
        }

        if (optionValues == null) {
            return null;
        }

        String optionValue = optionValues.getStringValueOrDefault(expression.trim());
        if (optionValue == null || optionValue.isEmpty()) {
            return null;
        }

        return parseFloat(optionValue);
    }

    Integer resolveDimensionForTesting(String expression, boolean relative, int base) {
        return resolveDimension(expression, relative, base, "test");
    }

    Integer resolveAbsoluteDimensionForTesting(String expression) {
        return resolveAbsoluteDimension(expression, "test");
    }

    private static Float parseFloat(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int safeBufferSize(long bytes, String imageName) {
        if (bytes <= 0L || bytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("Unable to clear custom image " + imageName
                + " with fallback path; slice size is " + bytes + " bytes");
        }
        return (int) bytes;
    }

    static int bytesPerPixel(PixelFormat format, PixelType type) {
        Integer packedBytes = packedBytesPerPixel(type);
        if (packedBytes != null) {
            return packedBytes;
        }
        return componentCount(format) * bytesPerComponent(type);
    }

    private static Integer packedBytesPerPixel(PixelType type) {
        switch (type) {
            case UNSIGNED_BYTE_3_3_2:
            case UNSIGNED_BYTE_2_3_3_REV:
                return 1;
            case UNSIGNED_SHORT_5_6_5:
            case UNSIGNED_SHORT_5_6_5_REV:
            case UNSIGNED_SHORT_4_4_4_4:
            case UNSIGNED_SHORT_4_4_4_4_REV:
            case UNSIGNED_SHORT_5_5_5_1:
            case UNSIGNED_SHORT_1_5_5_5_REV:
                return 2;
            case UNSIGNED_INT_8_8_8_8:
            case UNSIGNED_INT_8_8_8_8_REV:
            case UNSIGNED_INT_10_10_10_2:
            case UNSIGNED_INT_2_10_10_10_REV:
                return 4;
            default:
                return null;
        }
    }

    private static int componentCount(PixelFormat format) {
        switch (format) {
            case RED:
            case RED_INTEGER:
                return 1;
            case RG:
            case RG_INTEGER:
                return 2;
            case RGB:
            case BGR:
            case RGB_INTEGER:
            case BGR_INTEGER:
                return 3;
            case RGBA:
            case BGRA:
            case RGBA_INTEGER:
            case BGRA_INTEGER:
                return 4;
            default:
                return 4;
        }
    }

    private static int bytesPerComponent(PixelType type) {
        switch (type) {
            case BYTE:
            case UNSIGNED_BYTE:
                return 1;
            case SHORT:
            case UNSIGNED_SHORT:
            case HALF_FLOAT:
                return 2;
            case INT:
            case FLOAT:
            case UNSIGNED_INT:
            default:
                return 4;
        }
    }

    private static Throwable collectFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        suppressFailure(failure, exception);
        return failure;
    }

    private static void suppressFailure(Throwable failure, Throwable exception) {
        if (failure != exception) {
            failure.addSuppressed(exception);
        }
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected custom image failure", failure);
    }

    private void destroyTextures() {
        try {
            destroyTextureMap(textures, true);
        } finally {
            textures.clear();
        }
    }

    private void replaceTextures(Map<String, CustomImageTexture> newTextures, int resolvedWidth, int resolvedHeight) {
        Map<String, CustomImageTexture> oldTextures = new LinkedHashMap<>(textures);
        List<CustomImageTexture> registeredTextures = new ArrayList<>();
        try {
            for (CustomImageTexture texture : newTextures.values()) {
                TextureBindingRegistry.register(texture.data.getSamplerName(), texture.binding);
                registeredTextures.add(texture);
            }
        } catch (RuntimeException | Error exception) {
            rollbackNewTextureRegistration(registeredTextures, oldTextures, newTextures, exception);
            throw exception;
        }

        textures.clear();
        textures.putAll(newTextures);
        framebufferWidth = resolvedWidth;
        framebufferHeight = resolvedHeight;
        destroyTextureMap(oldTextures, true);
    }

    private void rollbackNewTextureRegistration(List<CustomImageTexture> registeredTextures,
                                                Map<String, CustomImageTexture> oldTextures,
                                                Map<String, CustomImageTexture> newTextures,
                                                Throwable failure) {
        for (CustomImageTexture texture : registeredTextures) {
            try {
                TextureBindingRegistry.unregister(texture.data.getSamplerName(), texture.binding);
            } catch (RuntimeException | Error exception) {
                suppressFailure(failure, exception);
            }
        }

        for (CustomImageTexture texture : oldTextures.values()) {
            try {
                TextureBindingRegistry.register(texture.data.getSamplerName(), texture.binding);
            } catch (RuntimeException | Error exception) {
                suppressFailure(failure, exception);
            }
        }

        try {
            destroyTextureMap(newTextures, false);
        } catch (RuntimeException | Error exception) {
            suppressFailure(failure, exception);
        }
    }

    private void destroyTextureMap(Map<String, CustomImageTexture> ownedTextures, boolean unregisterSamplers) {
        Throwable failure = null;
        try {
            for (CustomImageTexture texture : ownedTextures.values()) {
                if (unregisterSamplers) {
                    try {
                        TextureBindingRegistry.unregister(texture.data.getSamplerName(), texture.binding);
                    } catch (RuntimeException | Error exception) {
                        failure = collectFailure(failure, exception);
                    }
                }
                deleteTexture(texture.textureId, texture.data.getImageName());
            }
        } finally {
            ownedTextures.clear();
        }
        rethrowFailure(failure);
    }

    private static void deleteTexture(int textureId, String imageName) {
        if (textureId <= 0) {
            return;
        }

        try {
            GL11.glDeleteTextures(textureId);
        } catch (RuntimeException | Error exception) {
            Oculus.LOGGER.debug("Failed to delete custom image texture {}", imageName, exception);
        } finally {
            TextureLifecycleTracker.onDeleteTexture(textureId);
        }
    }

    private static final class Dimensions {
        private final int width;
        private final int height;
        private final int depth;

        private Dimensions(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    private static final class CustomImageTexture {
        private final CustomImageData data;
        private final Dimensions dimensions;
        private final int textureId;
        private final int target;
        private final TextureBinding binding;

        private CustomImageTexture(CustomImageData data, Dimensions dimensions, int textureId, int target,
                                   TextureBinding binding) {
            this.data = data;
            this.dimensions = dimensions;
            this.textureId = textureId;
            this.target = target;
            this.binding = binding;
        }
    }
}

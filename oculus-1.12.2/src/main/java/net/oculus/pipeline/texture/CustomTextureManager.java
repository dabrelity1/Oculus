package net.oculus.pipeline.texture;

import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipError;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import net.oculus.Oculus;
import net.oculus.gl.program.ProgramSamplers;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureFilteringData;
import net.oculus.shaderpack.texture.TextureStage;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.texture.format.TextureFormat;
import net.oculus.texture.format.TextureFormatLoader;
import net.oculus.texture.pbr.PBRTextureHolder;
import net.oculus.texture.pbr.PBRTextureManager;
import net.oculus.texture.pbr.PBRType;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Uploads shader-pack provided custom textures and wires them into sampler bindings.
 * The manager mirrors the modern Iris {@code CustomTextureManager} on the
 * source-backed 1.16.5 custom texture surface: PNG overrides, vanilla resource
 * references, lightmap redirects, and optional custom noise textures.
 */
public final class CustomTextureManager {
    private static final int DEFAULT_NOISE_TEXTURE_RESOLUTION = 256;
    private static final String[] NOISE_SAMPLER_ALIASES = {
        "oculus_noise",
        "custom_noise",
        "noise_texture",
        "noisetex"
    };

    private final EnumMap<TextureStage, Map<String, CustomTextureData>> rawTextureData;
    private final CustomTextureData rawNoiseTexture;
    private final int defaultNoiseTextureResolution;
    private final EnumMap<TextureStage, Map<String, TextureBinding>> stageBindings;
    private final List<Integer> ownedTextureIds;
    private TextureBinding noiseBinding;
    private boolean initialized;

    private CustomTextureManager(EnumMap<TextureStage, Map<String, CustomTextureData>> rawTextureData,
                                 CustomTextureData rawNoiseTexture,
                                 int defaultNoiseTextureResolution) {
        this.rawTextureData = rawTextureData == null ? new EnumMap<>(TextureStage.class) : rawTextureData;
        this.rawNoiseTexture = rawNoiseTexture;
        this.defaultNoiseTextureResolution = sanitizeNoiseTextureResolution(defaultNoiseTextureResolution);
        this.stageBindings = new EnumMap<>(TextureStage.class);
        this.ownedTextureIds = new ArrayList<>();
    }

    public static CustomTextureManager fromShaderPack(ShaderPack pack) {
        return fromShaderPack(pack, null);
    }

    public static CustomTextureManager fromShaderPack(ShaderPack pack, PackDirectives directives) {
        int noiseTextureResolution = directives == null
            ? DEFAULT_NOISE_TEXTURE_RESOLUTION
            : directives.getNoiseTextureResolution();
        if (pack == null) {
            return new CustomTextureManager(new EnumMap<>(TextureStage.class), null, noiseTextureResolution);
        }
        EnumMap<TextureStage, Map<String, CustomTextureData>> textures = pack.getCustomTextureDataMap();
        CustomTextureData noise = pack.getCustomNoiseTexture().orElse(null);
        return new CustomTextureManager(textures, noise, noiseTextureResolution);
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        try {
            Map<CustomTextureData, TextureBinding> bindingCache = new IdentityHashMap<>();
            rawTextureData.forEach((stage, samplerMap) -> {
                Map<String, TextureBinding> resolved = new HashMap<>();
                samplerMap.forEach((samplerName, data) -> {
                    TextureBinding binding = bindingCache.computeIfAbsent(data, textureData -> buildBinding(samplerName, textureData));
                    if (binding != null) {
                        resolved.put(samplerName, binding);
                    }
                });
                if (!resolved.isEmpty()) {
                    stageBindings.put(stage, resolved);
                }
            });

            if (rawNoiseTexture != null) {
                noiseBinding = bindingCache.computeIfAbsent(rawNoiseTexture, textureData -> buildBinding("noise_texture", textureData));
            }
            if (noiseBinding == null) {
                noiseBinding = buildDefaultNoiseBinding(defaultNoiseTextureResolution);
            }

            initialized = true;
        } catch (RuntimeException | Error exception) {
            cleanupAfterInitializeFailure(exception);
            throw exception;
        }
    }

    private void cleanupAfterInitializeFailure(Throwable failure) {
        try {
            destroy();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressFailure(failure, cleanupFailure);
        }
    }

    public void applyCustomSamplers(String programName, ProgramSamplers.Builder builder) {
        applyCustomSamplers(programName, builder, null);
    }

    public void applyCustomSamplers(String programName, ProgramSamplers.Builder builder, Iterable<Integer> flippedAtLeastOnce) {
        if (!initialized || builder == null || programName == null || stageBindings.isEmpty()) {
            return;
        }

        TextureStage stage = resolveStage(programName);
        if (stage == null) {
            return;
        }

        Map<String, TextureBinding> bindings = stageBindings.get(stage);
        if (bindings == null || bindings.isEmpty()) {
            return;
        }

        bindings.forEach((samplerName, binding) -> {
            if (!shouldApplyStageOverride(samplerName, flippedAtLeastOnce)) {
                return;
            }

            for (String overrideName : equivalentStageOverrideNames(samplerName)) {
                if (shouldApplyEquivalentStageOverride(samplerName, overrideName, builder)) {
                    builder.overrideBinding(overrideName, binding);
                }
            }
        });
    }

    static List<String> equivalentStageOverrideNames(String samplerName) {
        if (samplerName == null) {
            return new ArrayList<>();
        }

        List<String> names = new ArrayList<>();
        addEquivalentStageOverrideName(names, samplerName);

        if (isWorldAlbedoStageOverride(samplerName)) {
            addEquivalentStageOverrideName(names, "tex");
            addEquivalentStageOverrideName(names, "texture");
            addEquivalentStageOverrideName(names, "gtexture");
            addEquivalentStageOverrideName(names, "gcolor");
            addEquivalentStageOverrideName(names, "colortex0");
        }

        if ("gdepthtex".equals(samplerName) || "depthtex0".equals(samplerName)) {
            addEquivalentStageOverrideName(names, "gdepthtex");
            addEquivalentStageOverrideName(names, "depthtex0");
        }

        Integer renderTargetIndex = renderTargetIndexForStageOverride(samplerName);
        if (renderTargetIndex != null) {
            addEquivalentStageOverrideName(names, "colortex" + renderTargetIndex);
            if (renderTargetIndex < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                addEquivalentStageOverrideName(names,
                    PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(renderTargetIndex));
            }
        }

        return names;
    }

    private static boolean isWorldAlbedoStageOverride(String samplerName) {
        return isNonLowWorldAlbedoStageOverride(samplerName)
            || "gcolor".equals(samplerName)
            || "colortex0".equals(samplerName);
    }

    private static boolean isNonLowWorldAlbedoStageOverride(String samplerName) {
        return "tex".equals(samplerName)
            || "texture".equals(samplerName)
            || "gtexture".equals(samplerName);
    }

    private static boolean shouldApplyEquivalentStageOverride(String samplerName,
                                                             String overrideName,
                                                             ProgramSamplers.Builder builder) {
        if (isLowRenderTargetSamplerName(samplerName)
            && isNonLowWorldAlbedoStageOverride(overrideName)) {
            return builder != null && builder.hasRegisteredSamplerBinding(overrideName);
        }
        return shouldApplyUnregisteredStageOverride(overrideName, builder);
    }

    private static void addEquivalentStageOverrideName(List<String> names, String samplerName) {
        if (!names.contains(samplerName)) {
            names.add(samplerName);
        }
    }

    private static Integer renderTargetIndexForStageOverride(String samplerName) {
        Integer colortexIndex = parseColorTextureIndex(samplerName);
        if (colortexIndex != null && colortexIndex >= 0) {
            return colortexIndex;
        }

        int legacyIndex = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(samplerName);
        return legacyIndex >= 0 ? legacyIndex : null;
    }

    static boolean shouldApplyUnregisteredStageOverride(String samplerName, ProgramSamplers.Builder builder) {
        if (!requiresRegisteredStageOverride(samplerName)) {
            return true;
        }
        return builder != null && builder.hasRegisteredSamplerBinding(samplerName);
    }

    static boolean requiresRegisteredStageOverride(String samplerName) {
        return isLowRenderTargetSamplerName(samplerName)
            || isWorldDepthSamplerName(samplerName)
            || isCompositeOnlyDepthSamplerName(samplerName);
    }

    static boolean shouldApplyStageOverride(String samplerName, Iterable<Integer> flippedAtLeastOnce) {
        if (samplerName == null || flippedAtLeastOnce == null) {
            return true;
        }

        for (Integer flipped : flippedAtLeastOnce) {
            if (flipped == null || flipped < 0) {
                continue;
            }

            if (samplerName.equals("colortex" + flipped)) {
                return false;
            }

            if (flipped < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()
                && samplerName.equals(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(flipped))) {
                return false;
            }
        }

        return true;
    }

    static boolean isLowRenderTargetSamplerName(String samplerName) {
        return "gcolor".equals(samplerName)
            || "gdepth".equals(samplerName)
            || "gnormal".equals(samplerName)
            || "composite".equals(samplerName)
            || "gaux0".equals(samplerName)
            || isColorTextureIndexInRange(samplerName, 0, 3);
    }

    private static boolean isWorldDepthSamplerName(String samplerName) {
        return "depthtex0".equals(samplerName)
            || "depthtex1".equals(samplerName);
    }

    private static boolean isCompositeOnlyDepthSamplerName(String samplerName) {
        return "gdepthtex".equals(samplerName)
            || "depthtex2".equals(samplerName);
    }

    private static boolean isColorTextureIndexInRange(String samplerName, int min, int max) {
        Integer index = parseColorTextureIndex(samplerName);
        return index != null && index >= min && index <= max;
    }

    private static Integer parseColorTextureIndex(String samplerName) {
        if (samplerName == null || !samplerName.startsWith("colortex")) {
            return null;
        }
        try {
            return Integer.parseInt(samplerName.substring("colortex".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public void applyGlobalOverrides() {
        if (!initialized || noiseBinding == null) {
            return;
        }

        for (String alias : NOISE_SAMPLER_ALIASES) {
            TextureBindingRegistry.register(alias, noiseBinding);
        }
    }

    public int getNoiseTextureId() {
        return noiseBinding == null ? 0 : noiseBinding.getTextureId();
    }

    public void destroy() {
        Throwable failure = null;
        try {
            failure = unregisterNoiseSamplers(failure);

            for (Integer textureId : ownedTextureIds) {
                if (textureId != null) {
                    deleteOwnedTexture(textureId, "custom texture");
                }
            }
        } finally {
            ownedTextureIds.clear();
            stageBindings.clear();
            noiseBinding = null;
            initialized = false;
        }
        rethrowFailure(failure);
    }

    private Throwable unregisterNoiseSamplers(Throwable failure) {
        if (noiseBinding == null) {
            return failure;
        }

        for (String alias : NOISE_SAMPLER_ALIASES) {
            try {
                TextureBindingRegistry.unregister(alias, noiseBinding);
            } catch (RuntimeException | Error exception) {
                failure = collectFailure(failure, exception);
            }
        }
        return failure;
    }

    private static void deleteOwnedTexture(int textureId, String description) {
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

    private TextureBinding buildBinding(String samplerName, CustomTextureData data) {
        if (data instanceof CustomTextureData.PngData) {
            return buildPngBinding((CustomTextureData.PngData) data, samplerName);
        }

        if (data instanceof CustomTextureData.LightmapMarker) {
            return buildLightmapBinding();
        }

        if (data instanceof CustomTextureData.ResourceData) {
            return buildResourceBinding((CustomTextureData.ResourceData) data);
        }

        Oculus.LOGGER.warn("Unsupported custom texture type {} for sampler {}", data, samplerName);
        return null;
    }

    private TextureBinding buildPngBinding(CustomTextureData.PngData pngData, String samplerName) {
        byte[] content = pngData.getContent();
        if (content == null || content.length == 0) {
            return null;
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            BufferedImage image = TextureUtil.readBufferedImage(stream);
            if (image == null) {
                return null;
            }

            final int textureId = TextureUtil.glGenTextures();
            if (textureId <= 0) {
                throw new IllegalStateException("Failed to allocate custom texture " + samplerName);
            }
            boolean blur = shouldBlur(pngData.getFilteringData());
            boolean clamp = shouldClamp(pngData.getFilteringData());
            int previousTextureBinding = 0;
            boolean previousTextureCaptured = false;
            boolean success = false;
            Throwable setupFailure = null;
            Throwable restoreFailure = null;
            try {
                previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                previousTextureCaptured = true;
                GlStateManager.bindTexture(textureId);
                TextureUtil.uploadTextureImageAllocate(textureId, image, blur, clamp);
                trackPngTextureAllocation(textureId, image);
                ownedTextureIds.add(textureId);
                success = true;
                return TextureBinding.texture2D(() -> textureId);
            } catch (RuntimeException | Error exception) {
                setupFailure = exception;
                throw exception;
            } finally {
                try {
                    restorePreviousTextureBinding(previousTextureBinding, previousTextureCaptured, setupFailure);
                } catch (RuntimeException | Error exception) {
                    restoreFailure = exception;
                    throw exception;
                } finally {
                    if ((!success || restoreFailure != null) && textureId > 0) {
                        ownedTextureIds.remove(Integer.valueOf(textureId));
                        deleteOwnedTexture(textureId, "custom texture " + samplerName);
                    }
                }
            }
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to decode custom texture {}", samplerName, exception);
            return null;
        }
    }

    private TextureBinding buildResourceBinding(CustomTextureData.ResourceData resourceData) {
        TextureManager textureManager = getTextureManager();
        if (textureManager == null) {
            return null;
        }

        ResourceTextureReference reference;
        try {
            reference = resolveResourceTextureReference(resourceData);
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to resolve custom resource texture {}:{}",
                resourceData.getNamespace(), resourceData.getLocation(), exception);
            return null;
        }

        ensureTextureLoaded(textureManager, reference.location);

        return TextureBinding.texture2D(() -> {
            // Resource reloads can replace the texture object behind this location.
            // Re-query the manager for each sampler update instead of capturing an id here.
            TextureManager manager = getTextureManager();
            if (manager == null) {
                return 0;
            }
            ITextureObject texture = manager.getTexture(reference.location);
            if (texture == null) {
                return TextureUtil.MISSING_TEXTURE.getGlTextureId();
            }

            int baseTextureId = texture.getGlTextureId();
            if (reference.pbrType == null) {
                return baseTextureId;
            }

            PBRTextureHolder pbrHolder = PBRTextureManager.INSTANCE.getOrLoadHolder(baseTextureId);
            AbstractTexture pbrTexture;
            switch (reference.pbrType) {
                case NORMAL:
                    pbrTexture = pbrHolder.getNormalTexture();
                    break;
                case SPECULAR:
                    pbrTexture = pbrHolder.getSpecularTexture();
                    break;
                default:
                    throw new IllegalStateException("Unknown PBR type " + reference.pbrType);
            }

            int pbrTextureId = pbrTexture.getGlTextureId();
            TextureFormat textureFormat = TextureFormatLoader.getFormat();
            if (textureFormat != null) {
                textureFormat.setupTextureParameters(reference.pbrType, pbrTextureId);
            }
            return pbrTextureId;
        });
    }

    static ResourceTextureReference resolveResourceTextureReference(CustomTextureData.ResourceData resourceData) {
        PBRType pbrType = detectPbrType(resourceData.getLocation());
        String location = pbrType == null
            ? resourceData.getLocation()
            : stripPbrSuffix(resourceData.getLocation(), pbrType);
        return new ResourceTextureReference(
            new ResourceLocation(resourceData.getNamespace(), location),
            pbrType);
    }

    static PBRType detectPbrType(String location) {
        if (location == null) {
            return null;
        }

        return PBRType.fromFileLocation(removeExtension(location));
    }

    static String stripPbrSuffix(String location, PBRType pbrType) {
        if (location == null || pbrType == null) {
            return location;
        }

        int extensionIndex = extensionIndex(location);
        int suffixEnd = extensionIndex >= 0 ? extensionIndex : location.length();
        int suffixStart = suffixEnd - pbrType.getSuffix().length();
        if (suffixStart < 0
            || !location.regionMatches(suffixStart, pbrType.getSuffix(), 0, pbrType.getSuffix().length())) {
            return location;
        }

        return location.substring(0, suffixStart) + location.substring(suffixEnd);
    }

    private static String removeExtension(String location) {
        int extensionIndex = extensionIndex(location);
        return extensionIndex >= 0 ? location.substring(0, extensionIndex) : location;
    }

    private static int extensionIndex(String location) {
        if (location == null) {
            return -1;
        }

        int slash = location.lastIndexOf('/');
        int dot = location.lastIndexOf('.');
        return dot > slash ? dot : -1;
    }

    private TextureBinding buildLightmapBinding() {
        return TextureBinding.texture2D(() -> {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return 0;
            }

            EntityRenderer renderer = minecraft.entityRenderer;
            if (renderer == null) {
                return 0;
            }

            DynamicTexture lightmapTexture = getLightmapTexture(renderer);
            return lightmapTexture != null ? lightmapTexture.getGlTextureId() : 0;
        });
    }

    private TextureBinding buildDefaultNoiseBinding(int resolution) {
        int size = sanitizeNoiseTextureResolution(resolution);
        byte[] pixels = createDefaultNoisePixels(size);
        ByteBuffer buffer = BufferUtils.createByteBuffer(pixels.length);
        buffer.put(pixels);
        ((Buffer) buffer).flip();

        final int textureId = TextureUtil.glGenTextures();
        if (textureId <= 0) {
            throw new IllegalStateException("Failed to allocate generated noise texture");
        }
        int previousTextureBinding = 0;
        boolean previousTextureCaptured = false;
        boolean success = false;
        Throwable setupFailure = null;
        Throwable restoreFailure = null;
        try {
            previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            previousTextureCaptured = true;
            GlStateManager.bindTexture(textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                size,
                size,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer);
            TextureLifecycleTracker.onTexImage2D(textureId, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size);
            ownedTextureIds.add(textureId);
            success = true;
            return TextureBinding.texture2D(() -> textureId);
        } catch (RuntimeException | Error exception) {
            setupFailure = exception;
            throw exception;
        } finally {
            try {
                restorePreviousTextureBinding(previousTextureBinding, previousTextureCaptured, setupFailure);
            } catch (RuntimeException | Error exception) {
                restoreFailure = exception;
                throw exception;
            } finally {
                if ((!success || restoreFailure != null) && textureId > 0) {
                    ownedTextureIds.remove(Integer.valueOf(textureId));
                    deleteOwnedTexture(textureId, "generated noise texture");
                }
            }
        }
    }

    private static void restorePreviousTextureBinding(int previousTextureBinding, boolean previousTextureCaptured,
                                                      Throwable setupFailure) {
        if (!previousTextureCaptured) {
            return;
        }

        try {
            GlStateManager.bindTexture(previousTextureBinding);
        } catch (RuntimeException | Error restoreFailure) {
            if (setupFailure != null) {
                suppressFailure(setupFailure, restoreFailure);
                return;
            }
            throw restoreFailure;
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
        throw new IllegalStateException("Unexpected custom texture failure", failure);
    }

    static int sanitizeNoiseTextureResolution(int resolution) {
        return Math.max(1, resolution);
    }

    static byte[] createDefaultNoisePixels(int resolution) {
        int size = sanitizeNoiseTextureResolution(resolution);
        byte[] pixels = new byte[defaultNoisePixelBufferSize(size)];
        Random random = new Random(0L);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int color = random.nextInt() | 0xFF000000;
                int offset = ((y * size) + x) * 4;
                pixels[offset] = (byte) (color & 0xFF);
                pixels[offset + 1] = (byte) ((color >>> 8) & 0xFF);
                pixels[offset + 2] = (byte) ((color >>> 16) & 0xFF);
                pixels[offset + 3] = (byte) 0xFF;
            }
        }

        return pixels;
    }

    static int defaultNoisePixelBufferSize(int resolution) {
        int size = sanitizeNoiseTextureResolution(resolution);
        long byteCount = (long) size * (long) size * 4L;
        if (byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "noiseTextureResolution " + size + " is too large for generated fallback noise");
        }
        return (int) byteCount;
    }

    private DynamicTexture getLightmapTexture(EntityRenderer renderer) {
        if (renderer == null) {
            return null;
        }

        try {
            return ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, renderer, "lightmapTexture");
        } catch (RuntimeException ignored) {
            try {
                return ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, renderer, "field_78513_d");
            } catch (RuntimeException exception) {
                Oculus.LOGGER.warn("Failed to access EntityRenderer lightmap texture", exception);
                return null;
            }
        }
    }

    private TextureStage resolveStage(String programName) {
        String lower = programName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("shadowcomp")) {
            return TextureStage.SHADOWCOMP;
        }
        if (lower.startsWith("prepare")) {
            return TextureStage.PREPARE;
        }
        if (lower.startsWith("deferred")) {
            return TextureStage.DEFERRED;
        }
        if (lower.startsWith("composite") || "final".equals(lower)) {
            return TextureStage.COMPOSITE_AND_FINAL;
        }
        if (lower.startsWith("gbuffers") || lower.startsWith("shadow")) {
            return TextureStage.GBUFFERS_AND_SHADOW;
        }
        return null;
    }

    private static boolean shouldBlur(TextureFilteringData filteringData) {
        return filteringData != null && filteringData.shouldBlur();
    }

    private static boolean shouldClamp(TextureFilteringData filteringData) {
        return filteringData != null && filteringData.shouldClamp();
    }

    static void trackPngTextureAllocation(int textureId, BufferedImage image) {
        TextureLifecycleTracker.onTextureUtilAllocation2D(textureId, image.getWidth(), image.getHeight());
    }

    private void ensureTextureLoaded(TextureManager textureManager, ResourceLocation location) {
        if (textureManager.getTexture(location) != null) {
            return;
        }
        try {
            textureManager.loadTexture(location, new SimpleTexture(location));
        } catch (RuntimeException | ZipError exception) {
            Oculus.LOGGER.warn("Failed to eagerly load custom resource texture {}", location, exception);
        }
    }

    private TextureManager getTextureManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null ? minecraft.getTextureManager() : null;
    }

    static final class ResourceTextureReference {
        final ResourceLocation location;
        final PBRType pbrType;

        private ResourceTextureReference(ResourceLocation location, PBRType pbrType) {
            this.location = location;
            this.pbrType = pbrType;
        }
    }
}

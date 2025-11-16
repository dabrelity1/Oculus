package net.oculus.pipeline.texture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
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
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureFilteringData;
import net.oculus.shaderpack.texture.TextureStage;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.lwjgl.opengl.GL11;

/**
 * Uploads shader-pack provided custom textures and wires them into sampler bindings.
 * The manager mirrors the modern Iris {@code CustomTextureManager} but trims
 * functionality down to what the 1.12 backport currently needs: supporting PNG
 * overrides, vanilla resource references, lightmap redirects, and optional
 * custom noise textures.
 */
public final class CustomTextureManager {
    private final EnumMap<TextureStage, Map<String, CustomTextureData>> rawTextureData;
    private final CustomTextureData rawNoiseTexture;
    private final EnumMap<TextureStage, Map<String, TextureBinding>> stageBindings;
    private final List<Integer> ownedTextureIds;
    private TextureBinding noiseBinding;
    private boolean initialized;

    private CustomTextureManager(EnumMap<TextureStage, Map<String, CustomTextureData>> rawTextureData,
                                 CustomTextureData rawNoiseTexture) {
        this.rawTextureData = rawTextureData == null ? new EnumMap<>(TextureStage.class) : rawTextureData;
        this.rawNoiseTexture = rawNoiseTexture;
        this.stageBindings = new EnumMap<>(TextureStage.class);
        this.ownedTextureIds = new ArrayList<>();
    }

    public static CustomTextureManager fromShaderPack(ShaderPack pack) {
        if (pack == null) {
            return new CustomTextureManager(new EnumMap<>(TextureStage.class), null);
        }
        EnumMap<TextureStage, Map<String, CustomTextureData>> textures = pack.getCustomTextureDataMap();
        CustomTextureData noise = pack.getCustomNoiseTexture().orElse(null);
        return new CustomTextureManager(textures, noise);
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        rawTextureData.forEach((stage, samplerMap) -> {
            Map<String, TextureBinding> resolved = new HashMap<>();
            samplerMap.forEach((samplerName, data) -> {
                TextureBinding binding = buildBinding(samplerName, data);
                if (binding != null) {
                    resolved.put(samplerName.toLowerCase(Locale.ROOT), binding);
                }
            });
            if (!resolved.isEmpty()) {
                stageBindings.put(stage, resolved);
            }
        });

        if (rawNoiseTexture != null) {
            noiseBinding = buildBinding("noise_texture", rawNoiseTexture);
        }

        initialized = true;
    }

    public void applyCustomSamplers(String programName, ProgramSamplers.Builder builder) {
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

        bindings.forEach(builder::overrideBinding);
    }

    public void applyGlobalOverrides() {
        if (!initialized || noiseBinding == null) {
            return;
        }

        TextureBindingRegistry.register("oculus_noise", noiseBinding);
        TextureBindingRegistry.register("custom_noise", noiseBinding);
        TextureBindingRegistry.register("noise_texture", noiseBinding);
        TextureBindingRegistry.register("noisetex", noiseBinding);
    }

    public void destroy() {
        for (Integer textureId : ownedTextureIds) {
            if (textureId != null && textureId > 0) {
                GL11.glDeleteTextures(textureId);
            }
        }
        ownedTextureIds.clear();
        stageBindings.clear();
        noiseBinding = null;
        initialized = false;
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
            boolean blur = shouldBlur(pngData.getFilteringData());
            boolean clamp = shouldClamp(pngData.getFilteringData());
            GlStateManager.bindTexture(textureId);
            TextureUtil.uploadTextureImageAllocate(textureId, image, blur, clamp);
            GlStateManager.bindTexture(0);
            ownedTextureIds.add(textureId);
            return TextureBinding.texture2D(() -> textureId);
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

        ResourceLocation location = new ResourceLocation(resourceData.getNamespace(), resourceData.getLocation());
        ensureTextureLoaded(textureManager, location);

        return TextureBinding.texture2D(() -> {
            TextureManager manager = getTextureManager();
            if (manager == null) {
                return 0;
            }
            ITextureObject texture = manager.getTexture(location);
            return texture != null ? texture.getGlTextureId() : 0;
        });
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

    private void ensureTextureLoaded(TextureManager textureManager, ResourceLocation location) {
        if (textureManager.getTexture(location) != null) {
            return;
        }
        textureManager.loadTexture(location, new SimpleTexture(location));
    }

    private TextureManager getTextureManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null ? minecraft.getTextureManager() : null;
    }
}
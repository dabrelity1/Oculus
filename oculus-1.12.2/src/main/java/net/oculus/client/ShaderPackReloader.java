package net.oculus.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipError;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

import net.oculus.Oculus;
import net.oculus.config.OculusConfig;
import net.oculus.pipeline.PipelineManager;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderPackLoader;
import net.oculus.texture.format.TextureFormatLoader;

/**
 * Small helper that mirrors Iris' reload flow by delegating to the existing
 * pipeline manager.
 */
public final class ShaderPackReloader {
    private ShaderPackReloader() {
    }

    /**
     * Applies the shader pack selected in {@code oculus.properties}. This is the
     * 1.12.2 replacement for Iris/Oculus 1.16.5's render-system initialization
     * load path, where the persisted enabled flag and pack selection become the
     * active pack before a world pipeline is needed.
     *
     * @return {@code true} when an external shader pack was applied.
     */
    public static boolean applyConfiguredShaderPack() {
        TextureFormatLoader.registerReloadListener();

        OculusConfig config = Oculus.getConfig();
        if (config != null && !reloadConfig(config)) {
            prepareLoadedWorldPipeline();
            return false;
        }

        Path shaderpacksDirectory = getShaderpacksDirectory();

        boolean applied = applyConfiguredShaderPack(config, shaderpacksDirectory);
        prepareLoadedWorldPipeline();
        return applied;
    }

    static boolean applyConfiguredShaderPack(OculusConfig config, Path shaderpacksDirectory) {
        String packName = configuredPackName(config);
        if (packName == null) {
            disableShaders();
            return false;
        }

        if (shaderpacksDirectory == null) {
            Oculus.LOGGER.warn("Shaders are disabled because the Minecraft shaderpacks directory is not available");
            disableShaders();
            return false;
        }

        Map<String, String> overrides = config == null ? Collections.emptyMap() : config.getOptionOverrides(packName);
        try {
            ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacksDirectory, packName, overrides);
            PipelineManager.INSTANCE.reloadShaderPack(pack, pack.getOptionValues());
            Oculus.LOGGER.info("Using configured shader pack: {}", packName);
            return !pack.isInternal();
        } catch (Exception | ZipError exception) {
            Oculus.LOGGER.error("Failed to load configured shader pack {}", packName, exception);
            disableShaders();
            return false;
        }
    }

    /**
     * Reloads the currently applied shader pack if one exists.
     *
     * @return {@code true} when a reload ran, {@code false} otherwise.
     */
    public static boolean reload() {
        TextureFormatLoader.registerReloadListener();

        OculusConfig config = Oculus.getConfig();
        boolean reloaded;
        if (config != null) {
            if (!reloadConfig(config)) {
                return false;
            }
            reloaded = reload(config, getShaderpacksDirectory());
        } else {
            reloaded = reloadActiveShaderPack();
        }

        prepareLoadedWorldPipeline();
        return reloaded;
    }

    static boolean reload(OculusConfig config, Path shaderpacksDirectory) {
        if (config != null) {
            return applyConfiguredShaderPack(config, shaderpacksDirectory);
        }

        return reloadActiveShaderPack();
    }

    private static boolean reloadConfig(OculusConfig config) {
        try {
            config.initialize();
            return true;
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to reload Oculus config before shader reload", exception);
            return false;
        }
    }

    private static boolean reloadActiveShaderPack() {
        ShaderPack pack = PipelineManager.INSTANCE.getActivePack();
        if (pack == null || pack.isInternal()) {
            return false;
        }

        try {
            ShaderPack reloaded = ShaderPackLoader.load(pack.getName(), pack.getOptionValues().asMap());
            PipelineManager.INSTANCE.reloadShaderPack(reloaded, reloaded.getOptionValues());
            return true;
        } catch (Exception | ZipError exception) {
            Oculus.LOGGER.error("Failed to reload shader pack {}", pack.getName(), exception);
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendMessage(new TextComponentString("Failed to reload shaders: " + exception.getMessage()));
            }
            disableShaders();
            return false;
        }
    }

    public static void disableShaders() {
        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        if (activePack == null || !activePack.isInternal()) {
            PipelineManager.INSTANCE.reloadShaderPack(ShaderPack.internal(), ShaderPack.createEmptyOptionValues());
        }
        Oculus.LOGGER.info("Shaders are disabled");
    }

    private static String configuredPackName(OculusConfig config) {
        if (config == null) {
            return null;
        }

        if (!config.areShadersEnabled()) {
            Oculus.LOGGER.info("Shaders are disabled because shadersEnabled is false in oculus.properties");
            return null;
        }

        String validationOverride = OculusRuntimeValidation.getShaderPackOverride();
        if (validationOverride != null) {
            Oculus.LOGGER.info("Using runtime validation shader pack override: {}", validationOverride);
            return validationOverride;
        }

        String packName = config.getSelectedPackName();
        if (packName == null || packName.trim().isEmpty()) {
            Oculus.LOGGER.info("Shaders are disabled because no shader pack is selected in oculus.properties");
            return null;
        }

        return packName.trim();
    }

    private static Path getShaderpacksDirectory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameDir == null) {
            return null;
        }
        return minecraft.gameDir.toPath().resolve("shaderpacks");
    }

    private static void prepareLoadedWorldPipeline() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.world != null) {
            PipelineManager.INSTANCE.getPipeline();
        }
    }
}

package net.oculus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.oculus.bridge.OculusRelictiumBridge;
import net.oculus.client.OculusClientEvents;
import net.oculus.client.OculusKeyBindings;
import net.oculus.config.OculusConfig;
import net.oculus.texture.format.TextureFormatLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Oculus.MOD_ID, dependencies = "required-after:vintagium")
public final class Oculus {
    public static final String MOD_ID = "oculus";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static OculusConfig config;

    public Oculus() {
    }

    @Mod.EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            initializeConfig();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            initializeConfig();
            ensureShaderpacksFolderExists(event.getModConfigurationDirectory());
            TextureFormatLoader.registerReloadListener();
            OculusKeyBindings.register();
            MinecraftForge.EVENT_BUS.register(new OculusClientEvents());
        }

        if (event.getSide() == Side.CLIENT) {
            LOGGER.info("Oculus client pre-initialization: registering shader bridge callbacks");
        }

        LOGGER.info("Oculus pre-initialization: registering Relictium bridge listener");
        MinecraftForge.EVENT_BUS.register(new OculusRelictiumBridge());
    }

    public static OculusConfig getConfig() {
        return config;
    }

    private static void initializeConfig() {
        File configDir = Loader.instance().getConfigDir();
        if (configDir == null) {
            return;
        }
        Path configPath = configDir.toPath().resolve("oculus.properties");

        if (config == null) {
            config = new OculusConfig(configPath);
        }

        try {
            config.initialize();
        } catch (IOException exception) {
            LOGGER.warn("Failed to load Oculus configuration from {}", configPath, exception);
        }
    }

    private static void ensureShaderpacksFolderExists(File configDir) {
        if (configDir == null) {
            return;
        }

        File mcDir = configDir.getParentFile();
        if (mcDir == null) {
            return;
        }

        File shaderpacksDir = new File(mcDir, "shaderpacks");
        if (!shaderpacksDir.exists() && !shaderpacksDir.mkdirs()) {
            LOGGER.warn("Unable to create shaderpacks directory at {}", shaderpacksDir.getAbsolutePath());
        }
    }
}

package net.oculus;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.oculus.bridge.OculusRelictiumBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

@Mod(modid = Oculus.MOD_ID, dependencies = "required-after:vintagium")
public final class Oculus {
    public static final String MOD_ID = "oculus";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public Oculus() {
    }

    @Mod.EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            LOGGER.info("Oculus construction: Initializing Mixins");
            MixinBootstrap.init();
            final String configs = System.getProperty("mixin.configs");
            LOGGER.info("mixin.configs JVM property: {}", configs);
            Mixins.addConfiguration("oculus.mixins.json");
            LOGGER.info("Requested oculus.mixins.json configuration from Oculus constructor");
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            LOGGER.info("Oculus client pre-initialization: registering shader bridge callbacks");
        }

        LOGGER.info("Oculus pre-initialization: registering Relictium bridge listener");
        MinecraftForge.EVENT_BUS.register(new OculusRelictiumBridge());
    }
}

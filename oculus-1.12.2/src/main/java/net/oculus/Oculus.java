package net.oculus;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.oculus.bridge.OculusRelictiumBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.launch.MixinBootstrap;

@Mod(modid = Oculus.MOD_ID, dependencies = "required-after:relictium")
public final class Oculus {
    public static final String MOD_ID = "oculus";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Oculus pre-initialization: registering Relictium bridge listener");

        // Initialise the mixin subsystem before any optional configurations are loaded.
        MixinBootstrap.init();
        Mixins.addConfiguration("oculus.mixins.json");

        MinecraftForge.EVENT_BUS.register(new OculusRelictiumBridge());
    }
}

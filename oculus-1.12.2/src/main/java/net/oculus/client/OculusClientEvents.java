package net.oculus.client;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import net.oculus.Oculus;
import net.oculus.config.OculusConfig;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.pipeline.PipelineManager;
import net.oculus.shaderpack.ShaderPack;

/**
 * Handles client-only events such as keybind polling.
 */
public final class OculusClientEvents {
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        handleReload(minecraft);
        handleToggle(minecraft);
        handleGui(minecraft);
    }

    private void handleReload(Minecraft minecraft) {
        while (OculusKeyBindings.RELOAD.isPressed()) {
            OculusConfig config = Oculus.getConfig();
            ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
            if (config != null && config.areShadersEnabled() && activePack != null && !activePack.isInternal()) {
                if (ShaderPackReloader.reload()) {
                    notifyPlayer(minecraft.player, new TextComponentString("Shaders reloaded."));
                }
            } else {
                notifyPlayer(minecraft.player, new TextComponentString("Enable shaders in the GUI before reloading."));
            }
        }
    }

    private void handleToggle(Minecraft minecraft) {
        OculusConfig config = Oculus.getConfig();
        if (config == null) {
            return;
        }

        while (OculusKeyBindings.TOGGLE.isPressed()) {
            boolean newValue = !config.areShadersEnabled();
            config.setShadersEnabled(newValue);
            saveConfig(config);

            String message = newValue
                ? "Shaders marked as enabled. Use Apply in the GUI to load them."
                : "Shaders disabled.";
            notifyPlayer(minecraft.player, new TextComponentString(message));
        }
    }

    private void handleGui(Minecraft minecraft) {
        while (OculusKeyBindings.OPEN_GUI.isPressed()) {
            GuiScreen parent = minecraft.currentScreen;
            minecraft.displayGuiScreen(new ShaderPackScreen(parent));
        }
    }

    private void notifyPlayer(EntityPlayerSP player, TextComponentString component) {
        if (player != null && component != null) {
            player.sendMessage(component);
        }
    }

    private void saveConfig(OculusConfig config) {
        try {
            config.save();
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to write Oculus config", exception);
        }
    }
}

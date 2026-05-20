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

/**
 * Handles client-only events such as keybind polling.
 */
public final class OculusClientEvents {
    private boolean startupConfigApplied;
    private final OculusRuntimeValidation runtimeValidation = new OculusRuntimeValidation();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        applyStartupConfig(minecraft);
        runtimeValidation.onClientTick(minecraft);
        handleReload(minecraft);
        handleToggle(minecraft);
        handleGui(minecraft);
    }

    private void applyStartupConfig(Minecraft minecraft) {
        if (startupConfigApplied || minecraft.gameDir == null) {
            return;
        }

        startupConfigApplied = true;
        ShaderPackReloader.applyConfiguredShaderPack();
    }

    private void handleReload(Minecraft minecraft) {
        while (OculusKeyBindings.RELOAD.isPressed()) {
            boolean reloaded = ShaderPackReloader.reload();
            OculusConfig config = Oculus.getConfig();
            if (reloaded) {
                notifyPlayer(minecraft.player, new TextComponentString("Shaders reloaded."));
            } else if (config != null && !config.areShadersEnabled()) {
                notifyPlayer(minecraft.player, new TextComponentString("Enable shaders in the GUI before reloading."));
            } else {
                notifyPlayer(minecraft.player, new TextComponentString("No valid shader pack is selected."));
            }
        }
    }

    private void handleToggle(Minecraft minecraft) {
        OculusConfig config = Oculus.getConfig();
        if (config == null) {
            return;
        }

        while (OculusKeyBindings.TOGGLE.isPressed()) {
            boolean previousValue = config.areShadersEnabled();
            boolean newValue = !previousValue;
            config.setShadersEnabled(newValue);
            if (!saveConfig(config)) {
                config.setShadersEnabled(previousValue);
                notifyPlayer(minecraft.player, new TextComponentString("Failed to write Oculus config."));
                continue;
            }

            boolean reloaded = ShaderPackReloader.reload();
            String message = newValue && reloaded
                ? "Shaders enabled."
                : newValue
                    ? "No valid shader pack is selected."
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

    private boolean saveConfig(OculusConfig config) {
        try {
            config.save();
            return true;
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to write Oculus config", exception);
            return false;
        }
    }
}

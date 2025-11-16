package net.oculus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

import net.oculus.Oculus;
import net.oculus.pipeline.PipelineManager;
import net.oculus.shaderpack.ShaderPack;

/**
 * Small helper that mirrors Iris' reload flow by delegating to the existing
 * pipeline manager.
 */
public final class ShaderPackReloader {
    private ShaderPackReloader() {
    }

    /**
     * Reloads the currently applied shader pack if one exists.
     *
     * @return {@code true} when a reload ran, {@code false} otherwise.
     */
    public static boolean reload() {
        ShaderPack pack = PipelineManager.INSTANCE.getActivePack();
        if (pack == null || pack.isInternal()) {
            return false;
        }

        try {
            PipelineManager.INSTANCE.reloadShaderPack(pack, pack.getOptionValues());
            return true;
        } catch (Exception exception) {
            Oculus.LOGGER.error("Failed to reload shader pack {}", pack.getName(), exception);
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendMessage(new TextComponentString("Failed to reload shaders: " + exception.getMessage()));
            }
            return false;
        }
    }
}

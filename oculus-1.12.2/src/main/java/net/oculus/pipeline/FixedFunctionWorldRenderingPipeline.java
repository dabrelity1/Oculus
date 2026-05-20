package net.oculus.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.oculus.gl.program.Program;

/**
 * No-op pipeline used when shaders are disabled or fail to initialize. Vanilla's
 * fixed-function renderer remains responsible for the frame in this mode.
 */
public final class FixedFunctionWorldRenderingPipeline implements WorldRenderingPipeline {
    public FixedFunctionWorldRenderingPipeline() {
        BlockRenderingSettings.INSTANCE.setBlockStateIds(null);
        BlockRenderingSettings.INSTANCE.setRenderLayerOverrides(null);
        BlockRenderingSettings.INSTANCE.setEntityIds(null);
        BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(1.0F);
        BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(false);
        BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(false);
    }

    @Override
    public void beginLevelRendering() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Framebuffer framebuffer = minecraft == null ? null : minecraft.getFramebuffer();
        if (framebuffer != null) {
            framebuffer.bindFramebuffer(true);
        }

        Program.unbind();
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return true;
    }

    @Override
    public boolean shouldRenderVignette() {
        return true;
    }
}

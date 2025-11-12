package net.oculus.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL13;
import net.oculus.uniforms.SystemTimeUniforms;

/**
 * Ported skeleton of the 1.16.5 shader pipeline manager. The current 1.12.2 build keeps all
 * behaviour minimal while mirroring the public API relied on by the shader loader and mixins.
 */
public final class PipelineManager {
    public static final PipelineManager INSTANCE = new PipelineManager(dimension -> new FixedFunctionWorldRenderingPipeline());

    private static final Logger LOGGER = LogManager.getLogger("OculusPipeline");

    private final Function<NamespacedId, WorldRenderingPipeline> pipelineFactory;
    private final Map<NamespacedId, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();

    private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
    private int versionCounterForSodiumShaderReload;

    public PipelineManager(Function<NamespacedId, WorldRenderingPipeline> pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    public WorldRenderingPipeline preparePipeline(NamespacedId dimension) {
        WorldRenderingPipeline current = pipelinesPerDimension.get(dimension);

        if (current == null) {
            SystemTimeUniforms.COUNTER.reset();
            SystemTimeUniforms.TIMER.reset();

            LOGGER.info("Creating pipeline for dimension {}", dimension);
            current = pipelineFactory.apply(dimension);
            if (current == null) {
                current = new FixedFunctionWorldRenderingPipeline();
            }

            pipelinesPerDimension.put(dimension, current);

            if (BlockRenderingSettings.INSTANCE.isReloadRequired()) {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft != null && minecraft.renderGlobal != null) {
                    minecraft.renderGlobal.loadRenderers();
                }

                BlockRenderingSettings.INSTANCE.clearReloadRequired();
            }
        }

        pipeline = current;
        return pipeline;
    }

    public void beginWorldRendering(float partialTicks) {
        preparePipeline(NamespacedId.overworld());

        if (pipeline != null) {
            pipeline.beginWorldRendering(partialTicks);
        }
    }

    public void endWorldRendering() {
        if (pipeline != null) {
            pipeline.endWorldRendering();
        }
    }

    public Optional<WorldRenderingPipeline> getPipeline() {
        return Optional.ofNullable(pipeline);
    }

    public int getVersionCounterForSodiumShaderReload() {
        return versionCounterForSodiumShaderReload;
    }

    public void destroyPipeline() {
        pipelinesPerDimension.forEach((dimension, activePipeline) -> {
            LOGGER.info("Destroying pipeline {}", dimension);
            resetTextureState();
            activePipeline.destroy();
        });

        pipelinesPerDimension.clear();
        pipeline = null;
        versionCounterForSodiumShaderReload++;
    }

    private void resetTextureState() {
        for (int i = 0; i < 16; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
}

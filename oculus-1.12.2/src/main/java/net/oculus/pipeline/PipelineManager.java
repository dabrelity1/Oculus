package net.oculus.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL13;

import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.uniforms.SystemTimeUniforms;

/**
 * 1.12.2 port of the shader pipeline manager. The class decides whether the
 * fixed-function pipeline or the new shader-backed pipeline should handle world
 * rendering for a given dimension.
 */
public final class PipelineManager {
    public static final PipelineManager INSTANCE = new PipelineManager();

    private static final Logger LOGGER = LogManager.getLogger("OculusPipeline");

    private final Map<NamespacedId, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();

    private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
    private int versionCounterForSodiumShaderReload;

    private PipelineManager() {
    }

    public WorldRenderingPipeline preparePipeline(NamespacedId dimension) {
        WorldRenderingPipeline current = pipelinesPerDimension.get(dimension);
        boolean shadersEnabled = BlockRenderingSettings.INSTANCE.isReloadRequired();

        if (current == null) {
            SystemTimeUniforms.COUNTER.reset();
            SystemTimeUniforms.TIMER.reset();
            current = createPipeline(shadersEnabled);
            pipelinesPerDimension.put(dimension, current);
            logPipelineCreation(dimension, current);
        } else if (shadersEnabled && !(current instanceof ShaderWorldRenderingPipeline)) {
            current.destroy();
            current = createPipeline(true);
            pipelinesPerDimension.put(dimension, current);
            logPipelineCreation(dimension, current);
        } else if (!shadersEnabled && !(current instanceof FixedFunctionWorldRenderingPipeline)) {
            current.destroy();
            current = createPipeline(false);
            pipelinesPerDimension.put(dimension, current);
            logPipelineCreation(dimension, current);
        }

        if (shadersEnabled) {
            BlockRenderingSettings.INSTANCE.clearReloadRequired();
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.renderGlobal != null) {
                minecraft.renderGlobal.loadRenderers();
            }
        }

        pipeline = current;
        return pipeline;
    }

    private WorldRenderingPipeline createPipeline(boolean shadersEnabled) {
        if (shadersEnabled) {
            ProgramSet programs = new ProgramSet(ShaderPack.placeholder(), ShaderProperties.empty());
            return new ShaderWorldRenderingPipeline(programs);
        }

        return new FixedFunctionWorldRenderingPipeline();
    }

    private void logPipelineCreation(NamespacedId dimension, WorldRenderingPipeline pipeline) {
        LOGGER.info("Creating pipeline for dimension {}: {}", dimension, pipeline.getClass().getSimpleName());
    }

    public WorldRenderingPipeline getPipeline() {
        return preparePipeline(NamespacedId.overworld());
    }

    public Optional<WorldRenderingPipeline> getPipelineOptional() {
        return Optional.ofNullable(pipeline);
    }

    public void beginWorldRendering(float partialTicks) {
        getPipeline().beginWorldRendering(partialTicks);
    }

    public void endWorldRendering() {
        getPipeline().endWorldRendering();
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

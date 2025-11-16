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
import net.oculus.shaderpack.option.values.MutableOptionValues;
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
    private ShaderPack activePack = ShaderPack.placeholder();
    private boolean shadersEnabled;
    private int versionCounterForSodiumShaderReload;

    private PipelineManager() {
    }

    public WorldRenderingPipeline preparePipeline(NamespacedId dimension) {
        WorldRenderingPipeline current = pipelinesPerDimension.get(dimension);

        boolean needsPipeline = current == null
            || (shouldUseShaders() && !(current instanceof ShaderWorldRenderingPipeline))
            || (!shouldUseShaders() && !(current instanceof FixedFunctionWorldRenderingPipeline));

        if (needsPipeline) {
            SystemTimeUniforms.COUNTER.reset();
            SystemTimeUniforms.TIMER.reset();

            if (current != null) {
                current.destroy();
            }

            current = createPipeline(shouldUseShaders());
            pipelinesPerDimension.put(dimension, current);
            logPipelineCreation(dimension, current);
        }

        if (BlockRenderingSettings.INSTANCE.isReloadRequired()) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.renderGlobal != null) {
                minecraft.renderGlobal.loadRenderers();
            }
            BlockRenderingSettings.INSTANCE.clearReloadRequired();
        }

        pipeline = current;
        return pipeline;
    }

    private WorldRenderingPipeline createPipeline(boolean shadersEnabled) {
        if (shadersEnabled && hasUsableShaderPack()) {
            ShaderPack pack = activePack;
            ShaderProperties properties = pack.getProperties();
            ProgramSet programs = new ProgramSet(pack.getProgramRoot(), pack.getSourceProvider(), properties, pack);
            return new ShaderWorldRenderingPipeline(pack, programs, properties);
        }

        return new FixedFunctionWorldRenderingPipeline();
    }

    private void logPipelineCreation(NamespacedId dimension, WorldRenderingPipeline pipeline) {
        LOGGER.info("Creating pipeline for dimension {}: {}", dimension, pipeline.getClass().getSimpleName());
    }

    public WorldRenderingPipeline getPipeline() {
        return preparePipeline(NamespacedId.overworld());
    }

    public WorldRenderingPipeline getPipelineNullable() {
        return pipeline;
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

    public ShaderPack getActivePack() {
        return activePack != null ? activePack : ShaderPack.placeholder();
    }

    public void reloadShaderPack(ShaderPack pack, MutableOptionValues values) {
        ShaderPack nextPack = pack != null ? pack : ShaderPack.placeholder();
        if (values != null && nextPack.getOptionValues() != values) {
            MutableOptionValues packValues = nextPack.getOptionValues();
            values.asMap().forEach((key, value) -> {
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    packValues.setBooleanValue(key, Boolean.parseBoolean(value));
                } else {
                    packValues.setStringValue(key, value);
                }
            });
        }

        this.activePack = nextPack;
        this.shadersEnabled = hasUsableShaderPack();
        if (nextPack != null) {
            BlockContextHolder.useActiveStateMap(nextPack.getBlockStateIdMap().isEmpty() ? null : nextPack.getBlockStateIdMap());
        } else {
            BlockContextHolder.useActiveStateMap(null);
        }
        BlockRenderingSettings.INSTANCE.markReloadRequired();
        destroyPipeline();
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

    private boolean hasUsableShaderPack() {
        return activePack != null && !activePack.isInternal();
    }

    private boolean shouldUseShaders() {
        return shadersEnabled && hasUsableShaderPack();
    }
}

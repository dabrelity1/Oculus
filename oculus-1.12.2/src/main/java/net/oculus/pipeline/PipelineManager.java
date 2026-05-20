package net.oculus.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipError;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.oculus.blockrendering.BlockMaterialMapping;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.sampler.SamplerLimits;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.texture.pbr.PBRTextureManager;
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
    private final Map<String, String> failedPackBackoffs = new HashMap<>();

    private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
    private ShaderPack activePack = ShaderPack.internal();
    private String activePackBackoffKey = "";
    private boolean shadersEnabled;
    private boolean runtimeFallback;
    private int versionCounterForSodiumShaderReload;
    private NamespacedId lastDimension = NamespacedId.overworld();

    private PipelineManager() {
    }

    public WorldRenderingPipeline preparePipeline(NamespacedId dimension) {
        dimension = dimension == null ? NamespacedId.overworld() : dimension;
        lastDimension = dimension;
        WorldRenderingPipeline current = pipelinesPerDimension.get(dimension);

        boolean needsPipeline = current == null
            || (shouldUseShaders() && !(current instanceof ShaderWorldRenderingPipeline))
            || (!shouldUseShaders() && !(current instanceof FixedFunctionWorldRenderingPipeline));

        if (needsPipeline) {
            SystemTimeUniforms.COUNTER.reset();
            SystemTimeUniforms.TIMER.reset();

            if (current != null) {
                Throwable replacementFailure = null;
                replacementFailure = runTeardownStep(replacementFailure, dimension,
                    "resetting texture state before pipeline replacement", this::resetTextureState);
                replacementFailure = runTeardownStep(replacementFailure, dimension,
                    "destroying replaced pipeline", current::destroy);
                if (replacementFailure != null) {
                    LOGGER.warn("One or more pipeline replacement cleanup steps failed", replacementFailure);
                }
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
            try {
                ShaderPack pack = activePack;
                ShaderProperties properties = pack.getProperties();
                ProgramSet programs = pack.getProgramSet(lastDimension);
                return new ShaderWorldRenderingPipeline(pack, programs, properties);
            } catch (RuntimeException | ZipError ex) {
                LOGGER.error("Failed to create shader rendering pipeline, disabling shaders!", ex);
                recordFailedPackBackoff(ex);
            }
        }

        return new FixedFunctionWorldRenderingPipeline();
    }

    private void logPipelineCreation(NamespacedId dimension, WorldRenderingPipeline pipeline) {
        LOGGER.info("Creating pipeline for dimension {}: {}", dimension, pipeline.getClass().getSimpleName());
    }

    public WorldRenderingPipeline getPipeline() {
        return preparePipeline(getCurrentDimension());
    }

    public WorldRenderingPipeline getPipelineNullable() {
        return pipeline;
    }

    public Optional<WorldRenderingPipeline> getPipelineOptional() {
        return Optional.ofNullable(pipeline);
    }

    public void beginWorldRendering(float partialTicks) {
        WorldRenderingPipeline active = getPipeline();
        try {
            active.beginWorldRendering(partialTicks);
        } catch (RuntimeException | ZipError ex) {
            if (!(active instanceof ShaderWorldRenderingPipeline)) {
                throw ex;
            }

            activateRuntimeFallback(active, ex).beginWorldRendering(partialTicks);
        }
    }

    public void afterCameraSetup(float partialTicks) {
        WorldRenderingPipeline active = getPipelineNullable();
        if (active != null) {
            active.afterCameraSetup(partialTicks);
        }
    }

    public void endWorldRendering() {
        WorldRenderingPipeline active = getPipelineNullable();
        if (active != null) {
            active.finalizeLevelRendering();
        }
    }

    public int getVersionCounterForSodiumShaderReload() {
        return versionCounterForSodiumShaderReload;
    }

    public ShaderPack getActivePack() {
        return activePack != null ? activePack : ShaderPack.internal();
    }

    public void reloadShaderPack(ShaderPack pack, MutableOptionValues values) {
        ShaderPack nextPack = pack != null ? pack : ShaderPack.internal();
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

        String nextBackoffKey = createPackBackoffKey(nextPack);
        boolean nextRuntimeFallback = isBackedOff(nextBackoffKey);
        boolean nextShadersEnabled = hasUsableShaderPack(nextPack);
        boolean useShaderPipeline = nextShadersEnabled && !nextRuntimeFallback;
        Object2IntMap<IBlockState> blockStateIds = useShaderPipeline ? nextPack.getBlockStateIdMap() : null;
        Map<Block, BlockRenderLayer> renderLayerOverrides = useShaderPipeline
            ? BlockMaterialMapping.createRenderLayerMap(nextPack.getIdMap().getBlockRenderTypeMap())
            : null;
        Object2IntFunction<net.oculus.shaderpack.materialmap.NamespacedId> entityIds = useShaderPipeline
            ? nextPack.getIdMap().getEntityIdMap()
            : null;

        PBRTextureManager.INSTANCE.resetSamplerUsage();
        this.activePack = nextPack;
        this.activePackBackoffKey = nextBackoffKey;
        this.runtimeFallback = nextRuntimeFallback;
        this.shadersEnabled = nextShadersEnabled;
        if (this.runtimeFallback) {
            LOGGER.warn("Skipping shader pipeline retry for {} until the pack, profile, or option values change. Previous failure: {}",
                nextPack.getName(), failedPackBackoffs.get(activePackBackoffKey));
        }
        BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(useShaderPipeline);
        BlockRenderingSettings.INSTANCE.setBlockStateIds(blockStateIds);
        BlockRenderingSettings.INSTANCE.setRenderLayerOverrides(renderLayerOverrides);
        BlockRenderingSettings.INSTANCE.setEntityIds(entityIds);
        BlockRenderingSettings.INSTANCE.markReloadRequired();
        destroyPipeline();
    }

    public void destroyPipeline() {
        Throwable failure = null;
        try {
            for (Map.Entry<NamespacedId, WorldRenderingPipeline> entry : new ArrayList<>(pipelinesPerDimension.entrySet())) {
                NamespacedId dimension = entry.getKey();
                WorldRenderingPipeline activePipeline = entry.getValue();
                LOGGER.info("Destroying pipeline {}", dimension);
                failure = runTeardownStep(failure, dimension, "resetting texture state", this::resetTextureState);
                failure = runTeardownStep(failure, dimension, "destroying pipeline", activePipeline::destroy);
            }
        } finally {
            pipelinesPerDimension.clear();
            pipeline = null;
            versionCounterForSodiumShaderReload++;
        }

        if (failure != null) {
            LOGGER.warn("One or more pipeline teardown steps failed during reload cleanup", failure);
        }
    }

    private Throwable runTeardownStep(Throwable failure, NamespacedId dimension, String action, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            LOGGER.warn("Exception while {} for pipeline {}", action, dimension, exception);
            if (failure == null) {
                return exception;
            }
            suppressCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable exception) {
        if (failure != null && exception != null && exception != failure) {
            failure.addSuppressed(exception);
        }
    }

    private WorldRenderingPipeline activateRuntimeFallback(WorldRenderingPipeline failedPipeline, Throwable failure) {
        LOGGER.error("Failed to create shader rendering pipeline, disabling shaders!", failure);
        recordFailedPackBackoff(failure);

        Throwable cleanupFailure = null;
        cleanupFailure = runTeardownStep(cleanupFailure, lastDimension,
            "resetting texture state after shader failure", this::resetTextureState);
        cleanupFailure = runTeardownStep(cleanupFailure, lastDimension,
            "destroying failed shader pipeline", failedPipeline::destroy);
        if (cleanupFailure != null) {
            LOGGER.warn("One or more failed shader pipeline cleanup steps failed before fixed-function fallback was installed",
                cleanupFailure);
        }

        WorldRenderingPipeline fallback = new FixedFunctionWorldRenderingPipeline();
        pipelinesPerDimension.put(lastDimension, fallback);
        pipeline = fallback;
        versionCounterForSodiumShaderReload++;
        return fallback;
    }

    private void resetTextureState() {
        OculusRenderSystem.unbindTexture2DFromUnits(textureUnitCleanupCount());
    }

    private int textureUnitCleanupCount() {
        int minimumCleanupUnits = 16;
        try {
            return Math.max(minimumCleanupUnits, SamplerLimits.get().getMaxTextureUnits());
        } catch (RuntimeException | LinkageError ex) {
            LOGGER.debug("Unable to query sampler texture unit limit during pipeline teardown; using {} cleanup units",
                minimumCleanupUnits, ex);
            return minimumCleanupUnits;
        }
    }

    private boolean hasUsableShaderPack() {
        return hasUsableShaderPack(activePack);
    }

    private static boolean hasUsableShaderPack(ShaderPack pack) {
        return pack != null && !pack.isInternal();
    }

    private boolean shouldUseShaders() {
        return shadersEnabled && !runtimeFallback && hasUsableShaderPack() && !isBackedOff(activePackBackoffKey);
    }

    private void recordFailedPackBackoff(Throwable failure) {
        runtimeFallback = true;
        String key = activePackBackoffKey;
        if (key == null || key.isEmpty()) {
            key = createPackBackoffKey(activePack);
            activePackBackoffKey = key;
        }
        if (key != null && !key.isEmpty()) {
            failedPackBackoffs.put(key, summarizeFailure(failure));
        }
    }

    private boolean isBackedOff(String key) {
        return key != null && !key.isEmpty() && failedPackBackoffs.containsKey(key);
    }

    private static String summarizeFailure(Throwable failure) {
        if (failure == null) {
            return "unknown failure";
        }
        String message = failure.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }
        return failure.getClass().getName();
    }

    private static String createPackBackoffKey(ShaderPack pack) {
        if (pack == null || pack.isInternal()) {
            return "";
        }
        TreeMap<String, String> sortedValues = new TreeMap<>(pack.getOptionValues().asMap());
        return pack.getName() + "\n" + sortedValues.toString();
    }

    private NamespacedId getCurrentDimension() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null || minecraft.world.provider == null) {
            return lastDimension;
        }

        int dimension = minecraft.world.provider.getDimension();
        if (dimension == -1) {
            lastDimension = NamespacedId.nether();
        } else if (dimension == 1) {
            lastDimension = NamespacedId.end();
        } else if (dimension == 0) {
            lastDimension = NamespacedId.overworld();
        } else {
            lastDimension = NamespacedId.of("legacy", Integer.toString(dimension));
        }

        return lastDimension;
    }
}

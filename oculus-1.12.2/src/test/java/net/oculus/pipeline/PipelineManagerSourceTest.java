package net.oculus.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import org.junit.Test;
import net.oculus.shaderpack.IdMap;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.materialmap.BlockRenderType;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;
import net.oculus.shaderpack.texture.TextureStage;

public class PipelineManagerSourceTest {
    @Test
    public void reloadShaderPackPreservesEmptyShaderPackBlockStateIdMaps() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("Object2IntMap<IBlockState> blockStateIds = useShaderPipeline ? nextPack.getBlockStateIdMap() : null"));
        assertTrue(source.contains("Map<Block, BlockRenderLayer> renderLayerOverrides = useShaderPipeline"));
        assertTrue(source.contains("BlockMaterialMapping.createRenderLayerMap(nextPack.getIdMap().getBlockRenderTypeMap())"));
        assertTrue(source.contains("Object2IntFunction<net.oculus.shaderpack.materialmap.NamespacedId> entityIds = useShaderPipeline"));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.setBlockStateIds(blockStateIds);"));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.setRenderLayerOverrides(renderLayerOverrides);"));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.setEntityIds(entityIds);"));
        assertFalse(source.contains("getBlockStateIdMap().isEmpty() ? null"));
        assertFalse(source.contains("BlockContextHolder.useActiveStateMap(nextPack.getBlockStateIdMap()"));
    }

    @Test
    public void reloadShaderPackPrecomputesDerivedTerrainStateBeforePublishingPack() throws Exception {
        PipelineManager.INSTANCE.reloadShaderPack(ShaderPack.internal(), ShaderPack.createEmptyOptionValues());

        try {
            ShaderPack malformed = shaderPackWithMalformedRenderLayerMap();
            try {
                PipelineManager.INSTANCE.reloadShaderPack(malformed, malformed.getOptionValues());
                throw new AssertionError("Expected malformed render-layer map to fail before publication");
            } catch (RuntimeException expected) {
                // Expected: the malformed derived map is rejected before active-pack publication.
            }

            assertTrue(PipelineManager.INSTANCE.getActivePack().isInternal());
        } finally {
            PipelineManager.INSTANCE.reloadShaderPack(ShaderPack.internal(), ShaderPack.createEmptyOptionValues());
        }
    }

    @Test
    public void shaderPipelineCreationAndFirstFrameFailuresFallbackToFixedFunction() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("private boolean runtimeFallback;"));
        assertTrue(source.contains("import java.util.zip.ZipError;"));
        assertTrue(source.contains("} catch (RuntimeException | ZipError ex) {"));
        assertTrue(source.contains("return shadersEnabled && !runtimeFallback && hasUsableShaderPack() && !isBackedOff(activePackBackoffKey);"));
        assertTrue(source.contains("LOGGER.error(\"Failed to create shader rendering pipeline, disabling shaders!\", ex);"));
        assertTrue(source.contains("recordFailedPackBackoff(ex);"));
        assertTrue(source.contains("recordFailedPackBackoff(failure);"));
        assertTrue(source.contains("activateRuntimeFallback(active, ex).beginWorldRendering(partialTicks);"));
        assertTrue(source.contains("private WorldRenderingPipeline activateRuntimeFallback(WorldRenderingPipeline failedPipeline, Throwable failure)"));
        assertTrue(source.contains("private void recordFailedPackBackoff(Throwable failure)"));
        assertTrue(source.contains("private static String summarizeFailure(Throwable failure)"));
        assertTrue(source.contains("private final Map<String, String> failedPackBackoffs = new HashMap<>();"));
        assertTrue(source.contains("String nextBackoffKey = createPackBackoffKey(nextPack);"));
        assertTrue(source.contains("boolean nextRuntimeFallback = isBackedOff(nextBackoffKey);"));
        assertTrue(source.contains("this.activePackBackoffKey = nextBackoffKey;"));
        assertTrue(source.contains("this.runtimeFallback = nextRuntimeFallback;"));
        assertTrue(source.contains("Skipping shader pipeline retry for {} until the pack, profile, or option values change."));
        assertTrue(source.contains("new TreeMap<>(pack.getOptionValues().asMap())"));
    }

    @Test
    public void pbrSamplerUsageResetsPerPackReloadNotPerDimensionPipeline() throws Exception {
        String managerSource = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);
        String shaderPipelineSource = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);
        String reloadBody = methodBody(managerSource, "public void reloadShaderPack(ShaderPack pack, MutableOptionValues values)");

        int entityIds = reloadBody.indexOf(
            "Object2IntFunction<net.oculus.shaderpack.materialmap.NamespacedId> entityIds = useShaderPipeline");
        int reset = reloadBody.indexOf("PBRTextureManager.INSTANCE.resetSamplerUsage();", entityIds);
        int publish = reloadBody.indexOf("this.activePack = nextPack;", reset);
        int destroy = reloadBody.indexOf("destroyPipeline();", publish);

        assertTrue(managerSource.contains("import net.oculus.texture.pbr.PBRTextureManager;"));
        assertTrue("PBR usage should reset only after derived terrain state is ready", reset > entityIds);
        assertTrue("PBR usage should reset before publishing the next active pack", publish > reset);
        assertTrue("PBR usage should reset once for the pack reload before old pipelines are destroyed", destroy > publish);
        assertFalse("Per-dimension pipeline construction must not clear PBR usage from another loaded dimension",
            shaderPipelineSource.contains("PBRTextureManager.INSTANCE.resetSamplerUsage();"));
    }

    @Test
    public void pipelineTeardownCleansReportedSamplerUnitRangeWithHeadlessFallback() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("import net.oculus.gl.sampler.SamplerLimits;"));
        assertTrue(source.contains("OculusRenderSystem.unbindTexture2DFromUnits(textureUnitCleanupCount());"));
        assertTrue(source.contains("return Math.max(minimumCleanupUnits, SamplerLimits.get().getMaxTextureUnits());"));
        assertTrue(source.contains("return minimumCleanupUnits;"));
        assertFalse(source.contains("OculusRenderSystem.unbindTexture2DFromUnits(16);"));
    }

    @Test
    public void pipelineTeardownIsolatesFailuresAndAlwaysClearsReloadState() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);
        String destroyBody = methodBody(source, "public void destroyPipeline()");
        String runTeardownStep = methodBody(source,
            "private Throwable runTeardownStep(Throwable failure, NamespacedId dimension, String action, Runnable cleanup)");

        assertTrue(source.contains("import java.util.ArrayList;"));
        assertTrue(destroyBody.contains("for (Map.Entry<NamespacedId, WorldRenderingPipeline> entry : new ArrayList<>(pipelinesPerDimension.entrySet()))"));
        assertTrue(destroyBody.contains("runTeardownStep(failure, dimension, \"resetting texture state\", this::resetTextureState);"));
        assertTrue(destroyBody.contains("runTeardownStep(failure, dimension, \"destroying pipeline\", activePipeline::destroy);"));
        assertTrue(destroyBody.contains("finally {\n"
            + "            pipelinesPerDimension.clear();\n"
            + "            pipeline = null;\n"
            + "            versionCounterForSodiumShaderReload++;\n"
            + "        }"));
        assertTrue(source.contains("private Throwable runTeardownStep(Throwable failure, NamespacedId dimension, String action, Runnable cleanup)"));
        assertTrue(source.contains("private static void suppressCleanupFailure(Throwable failure, Throwable exception)"));
        assertTrue(source.contains("} catch (RuntimeException | Error exception) {"));
        assertTrue(source.contains("LOGGER.warn(\"Exception while {} for pipeline {}\", action, dimension, exception);"));
        assertTrue(source.contains("LOGGER.warn(\"One or more pipeline teardown steps failed during reload cleanup\", failure);"));
        assertTrue(runTeardownStep.contains("suppressCleanupFailure(failure, exception);"));
        assertFalse(runTeardownStep.contains("failure.addSuppressed(exception);"));
        assertFalse(destroyBody.contains("pipelinesPerDimension.forEach("));
        assertFalse(destroyBody.contains("activePipeline.destroy();\n        });"));
    }

    @Test
    public void pipelineTeardownSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same teardown failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void pipelineTeardownSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("pipeline teardown failure");
        RuntimeException cleanupFailure = new RuntimeException("later pipeline cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void pipelineReplacementIsolatesCleanupFailuresBeforeCreatingReplacement() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);
        String prepareBody = methodBody(source, "public WorldRenderingPipeline preparePipeline(NamespacedId dimension)");

        int needsPipeline = prepareBody.indexOf("if (needsPipeline) {");
        int currentCheck = prepareBody.indexOf("if (current != null)", needsPipeline);
        int cleanupStart = prepareBody.indexOf("Throwable replacementFailure = null;", currentCheck);
        int reset = prepareBody.indexOf(
            "runTeardownStep(replacementFailure, dimension,\n"
                + "                    \"resetting texture state before pipeline replacement\", this::resetTextureState);",
            cleanupStart);
        int destroy = prepareBody.indexOf(
            "runTeardownStep(replacementFailure, dimension,\n"
                + "                    \"destroying replaced pipeline\", current::destroy);",
            reset);
        int warning = prepareBody.indexOf("One or more pipeline replacement cleanup steps failed", destroy);
        int create = prepareBody.indexOf("current = createPipeline(shouldUseShaders());", warning);
        int put = prepareBody.indexOf("pipelinesPerDimension.put(dimension, current);", create);

        assertTrue(needsPipeline >= 0);
        assertTrue(currentCheck > needsPipeline);
        assertTrue(cleanupStart > currentCheck);
        assertTrue(reset > cleanupStart);
        assertTrue(destroy > reset);
        assertTrue(warning > destroy);
        assertTrue(create > warning);
        assertTrue(put > create);
        assertFalse(prepareBody.contains("if (current != null) {\n                current.destroy();"));
    }

    @Test
    public void runtimeFallbackInstallsFixedFunctionEvenWhenCleanupFails() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/PipelineManager.java")), StandardCharsets.UTF_8);
        String fallbackBody = methodBody(source,
            "private WorldRenderingPipeline activateRuntimeFallback(WorldRenderingPipeline failedPipeline, Throwable failure)");

        int record = fallbackBody.indexOf("recordFailedPackBackoff(failure);");
        int cleanupStart = fallbackBody.indexOf("Throwable cleanupFailure = null;", record);
        int reset = fallbackBody.indexOf(
            "runTeardownStep(cleanupFailure, lastDimension,\n"
                + "            \"resetting texture state after shader failure\", this::resetTextureState);",
            cleanupStart);
        int destroy = fallbackBody.indexOf(
            "runTeardownStep(cleanupFailure, lastDimension,\n"
                + "            \"destroying failed shader pipeline\", failedPipeline::destroy);",
            reset);
        int cleanupWarning = fallbackBody.indexOf(
            "One or more failed shader pipeline cleanup steps failed before fixed-function fallback was installed",
            destroy);
        int createFallback = fallbackBody.indexOf("WorldRenderingPipeline fallback = new FixedFunctionWorldRenderingPipeline();",
            cleanupWarning);
        int putFallback = fallbackBody.indexOf("pipelinesPerDimension.put(lastDimension, fallback);", createFallback);
        int assignFallback = fallbackBody.indexOf("pipeline = fallback;", putFallback);
        int versionBump = fallbackBody.indexOf("versionCounterForSodiumShaderReload++;", assignFallback);
        int returnFallback = fallbackBody.indexOf("return fallback;", versionBump);

        assertTrue(record >= 0);
        assertTrue(cleanupStart > record);
        assertTrue(reset > cleanupStart);
        assertTrue(destroy > reset);
        assertTrue(cleanupWarning > destroy);
        assertTrue(createFallback > cleanupWarning);
        assertTrue(putFallback > createFallback);
        assertTrue(assignFallback > putFallback);
        assertTrue(versionBump > assignFallback);
        assertTrue(returnFallback > versionBump);
        assertFalse(fallbackBody.contains("resetTextureState();\n        try"));
        assertFalse(fallbackBody.contains("catch (RuntimeException destroyFailure)"));
    }

    private static ShaderPack shaderPackWithMalformedRenderLayerMap() throws Exception {
        return ShaderPack.of(
            "malformed-render-layer",
            OptionMenuContainer.EMPTY,
            ShaderProperties.empty(),
            ShaderPack.createEmptyOptionValues(),
            AbsolutePackPath.fromAbsolutePath("/"),
            path -> null,
            new EnumMap<>(TextureStage.class),
            null,
            malformedIdMap(),
            Object2IntMaps.emptyMap(),
            ProfileSet.empty());
    }

    private static IdMap malformedIdMap() throws Exception {
        Map<net.oculus.shaderpack.materialmap.NamespacedId, BlockRenderType> renderTypes = new HashMap<>();
        renderTypes.put(null, BlockRenderType.CUTOUT);

        Constructor<IdMap> constructor = IdMap.class.getDeclaredConstructor(
            it.unimi.dsi.fastutil.objects.Object2IntMap.class,
            it.unimi.dsi.fastutil.objects.Object2IntMap.class,
            it.unimi.dsi.fastutil.ints.Int2ObjectMap.class,
            Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
            Object2IntMaps.emptyMap(),
            Object2IntMaps.emptyMap(),
            Int2ObjectMaps.emptyMap(),
            renderTypes);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method signature " + signature, signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body for " + signature, bodyStart >= 0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, index);
                }
            }
        }

        throw new AssertionError("Unterminated method body for " + signature);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = PipelineManager.class.getDeclaredMethod("suppressCleanupFailure",
            Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }
}

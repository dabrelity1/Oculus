package net.oculus.uniforms.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderPackLoader;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.materialmap.NamespacedId;
import net.oculus.uniforms.BuiltinReplacementUniforms;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.GameplayUniforms;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.SystemTimeUniforms;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CustomUniformExpressionManagerTest {
    private static final String DISABLE_GL_STRING_PROBES_PROPERTY = "oculus.disableGlStringProbes";
    private static String previousGlStringProbeSetting;
    private static String previousGlCapabilityProbeSetting;

    @BeforeClass
    public static void disableGlProbesForHeadlessShaderPackLoads() {
        previousGlStringProbeSetting = System.getProperty(DISABLE_GL_STRING_PROBES_PROPERTY);
        previousGlCapabilityProbeSetting = System.getProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        System.setProperty(DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, "true");
    }

    @AfterClass
    public static void restoreGlProbeSettings() {
        restoreProperty(DISABLE_GL_STRING_PROBES_PROPERTY, previousGlStringProbeSetting);
        restoreProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, previousGlCapabilityProbeSetting);
    }

    @Before
    public void resetFrameStateBefore() {
        SystemTimeUniforms.COUNTER.reset();
        SystemTimeUniforms.TIMER.reset();
        BuiltinReplacementUniforms.setColorModulator(1.0F, 1.0F, 1.0F, 1.0F);
        GameplayUniforms.clearEntityColor();
        resetCapturedVectors();
    }

    @After
    public void resetFrameStateAfter() {
        SystemTimeUniforms.COUNTER.reset();
        SystemTimeUniforms.TIMER.reset();
        BuiltinReplacementUniforms.setColorModulator(1.0F, 1.0F, 1.0F, 1.0F);
        GameplayUniforms.clearEntityColor();
        resetCapturedVectors();
    }

    @Test
    public void smoothWithZeroHalfLifeConvergesImmediately() {
        ShaderProperties properties = new ShaderProperties(
            "variable.float.moving = frameCounter > 1\n" +
            "uniform.float.moved = smooth(2, moving, 0, 31536000)\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(0L);
        manager.beginFrame();
        assertEquals(0.0, manager.evaluateUniformForTesting("moved"), 0.0);

        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(100_000_000L);
        manager.beginFrame();
        assertEquals(1.0, manager.evaluateUniformForTesting("moved"), 0.0);
    }

    @Test
    public void complementaryStyleExpressionsCompileWithKnownSymbols() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.isEyeInCave = if(isEyeInWater == 0, 1.0 - smooth(202, if(eyeAltitude < 5.0, eyeBrightness.y / 240.0, 1.0), 6, 12), 0.0)\n" +
            "uniform.float.inDry = smooth(101, if(in(biome_precipitation, 0), 1, 0), 20, 10)\n" +
            "variable.float.difX = cameraPosition.x - previousCameraPosition.x\n" +
            "variable.float.moving = if(abs(difX) > 0.0 && abs(difX) < 1.0, 1, 0)\n" +
            "uniform.float.inNetherWastes = smooth(50, if(in(biome, BIOME_NETHER_WASTES), 1, 0), 15, 15)\n" +
            "uniform.float.maxBlindnessDarkness = max(blindness, darknessFactor)\n"
        );

        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        assertEquals(true, manager.hasUniform("isEyeInCave"));
        assertEquals(true, manager.hasUniform("inDry"));
        assertEquals(true, manager.hasUniform("inNetherWastes"));
        assertEquals(true, manager.hasUniform("maxBlindnessDarkness"));
    }

    @Test
    public void biomeConstantsResolveWithoutMinecraftBootstrap() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.netherWastesId = BIOME_NETHER_WASTES\n" +
            "uniform.float.endId = BIOME_THE_END\n" +
            "uniform.float.absentId = BIOME_PALE_GARDEN\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertEquals(8.0, manager.evaluateUniformForTesting("netherWastesId"), 0.0);
        assertEquals(9.0, manager.evaluateUniformForTesting("endId"), 0.0);
        assertTrue(manager.evaluateUniformForTesting("absentId") < 0.0);
    }

    @Test
    public void complementaryRuntimeCustomUniformBlockCompilesAndEvaluatesFromTargetPack() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        org.junit.Assume.assumeTrue("Complementary Reimagined shader pack is not available",
            Files.isDirectory(shaderpacks.resolve("ComplementaryReimagined_r5.6.1")));
        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, "ComplementaryReimagined_r5.6.1");
        ShaderProperties properties = pack.getProperties();

        assertEquals(19, properties.getCustomUniforms().size());
        assertEquals(6, properties.getCustomVariables().size());
        assertTrue(properties.getCustomUniforms().containsKey("framemod2"));
        assertTrue(properties.getCustomUniforms().containsKey("inPaleGarden"));
        assertTrue(properties.getCustomVariables().containsKey("moved"));
        assertTrue(properties.getCustomUniforms().get("eyeBrightnessM").getExpression().startsWith("smooth(4,"));
        assertTrue(properties.getCustomUniforms().get("eyeBrightnessM2").getExpression().startsWith("smooth(4,"));
        assertTrue(properties.getCustomUniforms().get("inSoulValley").getExpression().startsWith("smooth(54,"));
        assertTrue(properties.getCustomUniforms().get("inPaleGarden").getExpression().startsWith("smooth(54,"));

        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);
        for (String name : properties.getCustomUniforms().keySet()) {
            assertTrue("Missing compiled Complementary custom uniform " + name, manager.hasUniform(name));
        }

        for (int i = 0; i < 19; i++) {
            SystemTimeUniforms.COUNTER.beginFrame();
        }
        manager.beginFrame();

        assertEquals(1.0, manager.evaluateUniformForTesting("framemod2"), 0.0);
        assertEquals(3.0, manager.evaluateUniformForTesting("framemod4"), 0.0);
        assertEquals(3.0, manager.evaluateUniformForTesting("framemod8"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("inDry"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("inRainy"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("inSnowy"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("starter"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("frameTimeSmooth"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("rainFactor"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("inNetherWastes"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("inPaleGarden"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("maxBlindnessDarkness"), 0.0);
    }

    @Test
    public void makeupScalarExpressionsUseOptifineFunctionsAndMatrixAccess() {
        ShadowUniforms.configure(null);
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.inv_aspect_ratio = 1.0 / aspectRatio\n" +
            "uniform.float.externalScalars = u_ViewWidth + u_ViewHeight + iris_LineWidth + iris_ModelOffset + u_ModelScale + u_TextureScale\n" +
            "uniform.int.frame_mod = fmod(frameCounter, 16)\n" +
            "uniform.float.negative_mod = fmod(-1, 16)\n" +
            "uniform.float.fov_y_inv = 1.0 / atan(1.0 / gbufferProjection.1.1) * 0.5\n" +
            "uniform.float.matrixAliases = u_ModelViewMatrix.0.0 + u_ProjectionMatrix.1.1 + iris_ModelViewMatrix.2.2 + iris_ModelViewMat.0.0 + iris_ProjectionMatrix.3.3 + iris_ProjMat.0.0 + iris_TextureMat.0.0 + u_ModelViewProjectionMatrix.1.1 + iris_ModelViewProjectionMatrix.2.2 + iris_NormalMatrix.3.3\n" +
            "uniform.float.shadowMatrices = shadowProjection.3.3 + shadowProjectionMatrix.3.3 + shadowProjectionInverse.3.3 + shadowProjectionMatrixInverse.3.3 + shadowModelView.3.3 + shadowModelViewInverse.3.3\n" +
            "uniform.float.mixed = mix(2, 10, 0.25)\n" +
            "uniform.float.degrees = todeg(torad(90))\n" +
            "uniform.float.randomBounded = random(3, 4)\n" +
            "uniform.int.randomIntBounded = randomInt(5, 6)\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        for (int i = 0; i < 19; i++) {
            SystemTimeUniforms.COUNTER.beginFrame();
        }
        manager.beginFrame();

        assertEquals(1.0, manager.evaluateUniformForTesting("inv_aspect_ratio"), 0.0);
        assertEquals(1.0 + GameplayUniforms.getTerrainModelScale() + GameplayUniforms.getTerrainTextureScale(),
            manager.evaluateUniformForTesting("externalScalars"), 0.0);
        assertEquals(3.0, manager.evaluateUniformForTesting("frame_mod"), 0.0);
        assertEquals(15.0, manager.evaluateUniformForTesting("negative_mod"), 0.0);
        assertEquals(2.0 / Math.PI, manager.evaluateUniformForTesting("fov_y_inv"), 1.0e-6);
        assertEquals(10.0, manager.evaluateUniformForTesting("matrixAliases"), 0.0);
        assertEquals(6.0, manager.evaluateUniformForTesting("shadowMatrices"), 0.0);
        assertEquals(4.0, manager.evaluateUniformForTesting("mixed"), 0.0);
        assertEquals(90.0, manager.evaluateUniformForTesting("degrees"), 1.0e-6);
        assertTrue(manager.evaluateUniformForTesting("randomBounded") >= 3.0);
        assertTrue(manager.evaluateUniformForTesting("randomBounded") < 4.0);
        assertEquals(5.0, manager.evaluateUniformForTesting("randomIntBounded"), 0.0);
    }

    @Test
    public void liveExternalMatrixAliasesUseCurrentGlSourcesWithHeadlessFallbacks() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/uniforms/custom/CustomUniformExpressionManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("MatrixState::updateModelViewMatrix"));
        assertTrue(source.contains("MatrixState::updateProjectionMatrix"));
        assertTrue(source.contains("MatrixState::updateTextureMatrix"));
        assertTrue(source.contains("currentMatrixOrFallback("));

        ShaderProperties properties = new ShaderProperties(
            "uniform.float.live_matrices = iris_ModelViewMat.0.0 + iris_ProjMat.0.0 + "
                + "iris_TextureMat.0.0 + u_ModelViewProjectionMatrix.0.0 + "
                + "iris_ModelViewProjectionMatrix.0.0 + iris_NormalMatrix.0.0\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertTrue("dependencies=" + manager.dependenciesForTesting("live_matrices")
                + ", dynamicDependencies=" + manager.dynamicDependenciesForTesting("live_matrices"),
            manager.isUniformDynamicForTesting("live_matrices"));
        assertTrue(manager.dynamicDependenciesForTesting("live_matrices").contains("iris_ModelViewMat"));
        assertTrue(manager.dynamicDependenciesForTesting("live_matrices").contains("iris_ProjMat"));
        assertTrue(manager.dynamicDependenciesForTesting("live_matrices").contains("iris_TextureMat"));
        assertTrue(manager.dynamicDependenciesForTesting("live_matrices").contains("u_ModelViewProjectionMatrix"));
        assertTrue(manager.dynamicDependenciesForTesting("live_matrices").contains("iris_ModelViewProjectionMatrix"));
        assertTrue(manager.dynamicDependenciesForTesting("live_matrices").contains("iris_NormalMatrix"));
        assertEquals(6.0, manager.evaluateUniformForTesting("live_matrices"), 0.0);
    }

    @Test
    public void capturedAndShadowMatrixAliasesAreDynamicCustomExpressionDependencies() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.captured_matrices = gbufferModelView.0.0 + iris_ModelViewMatrix.0.0 + "
                + "gbufferPreviousModelView.0.0 + gbufferModelViewInverse.0.0 + "
                + "gbufferProjection.0.0 + iris_ProjectionMatrix.0.0 + "
                + "gbufferPreviousProjection.0.0 + gbufferProjectionInverse.0.0 + "
                + "shadowModelView.0.0 + shadowModelViewInverse.0.0 + "
                + "shadowProjection.0.0 + shadowProjectionMatrix.0.0 + "
                + "shadowProjectionInverse.0.0 + shadowProjectionMatrixInverse.0.0 + "
                + "iris_LightmapTextureMatrix.0.0\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        assertTrue("dependencies=" + manager.dependenciesForTesting("captured_matrices")
                + ", dynamicDependencies=" + manager.dynamicDependenciesForTesting("captured_matrices"),
            manager.isUniformDynamicForTesting("captured_matrices"));
        for (String dependency : new String[] {
                "gbufferModelView",
                "iris_ModelViewMatrix",
                "gbufferPreviousModelView",
                "gbufferModelViewInverse",
                "gbufferProjection",
                "iris_ProjectionMatrix",
                "gbufferPreviousProjection",
                "gbufferProjectionInverse",
                "shadowModelView",
                "shadowModelViewInverse",
                "shadowProjection",
                "shadowProjectionMatrix",
                "shadowProjectionInverse",
                "shadowProjectionMatrixInverse",
                "iris_LightmapTextureMatrix"
        }) {
            assertTrue(dependency + " missing from "
                    + manager.dynamicDependenciesForTesting("captured_matrices"),
                manager.dynamicDependenciesForTesting("captured_matrices").contains(dependency));
        }
    }

    @Test
    public void makeupRuntimeCustomUniformBlockCompilesAndEvaluatesFromTargetPack() throws Exception {
        ShadowUniforms.configure(null);
        Path shaderpacks = Paths.get("run", "shaderpacks");
        org.junit.Assume.assumeTrue("MakeUp UltraFast shader pack is not available",
            Files.isDirectory(shaderpacks.resolve("MakeUp-UltraFast-9.3e")));
        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(shaderpacks, "MakeUp-UltraFast-9.3e");
        ShaderProperties properties = pack.getProperties();

        assertEquals(11, properties.getCustomUniforms().size());
        assertEquals(17, properties.getCustomVariables().size());
        assertTrue(properties.getCustomUniforms().containsKey("taa_offset"));
        assertFalse(properties.getCustomUniforms().containsKey("dither_shift"));

        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);
        for (String name : properties.getCustomUniforms().keySet()) {
            assertTrue("Missing compiled MakeUp custom uniform " + name, manager.hasUniform(name));
        }

        for (int i = 0; i < 19; i++) {
            SystemTimeUniforms.COUNTER.beginFrame();
        }
        manager.beginFrame();

        assertEquals(0.0, manager.evaluateUniformForTesting("pixel_size_x"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("pixel_size_y"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("inv_aspect_ratio"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("day_moment"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("day_mixer"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("night_mixer"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("vol_mixer"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("light_mix"), 0.0);
        assertEquals(3.0, manager.evaluateUniformForTesting("frame_mod"), 0.0);
        assertEquals(2.0 / Math.PI, manager.evaluateUniformForTesting("fov_y_inv"), 1.0e-6);

        float[] taaOffset = manager.evaluateUniformVectorForTesting("taa_offset", 2);
        assertEquals(0.0F, taaOffset[0], 0.0F);
        assertEquals(0.0F, taaOffset[1], 0.0F);
    }

    @Test
    public void customExpressionsCanReadFixedScalarUniformSymbols() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.playerFlags = hideGUI + isSpectator + is_sneaking + is_sprinting + is_hurt + is_invisible + is_burning + is_on_ground + heavyFog\n" +
            "uniform.float.firstPerson = firstPersonCamera\n" +
            "uniform.float.playerValues = currentPlayerHealth + maxPlayerHealth + currentPlayerHunger + maxPlayerHunger + currentPlayerAir + maxPlayerAir\n" +
            "uniform.float.handValues = heldItemId + heldItemId2 + heldBlockLightValue + heldBlockLightValue2\n" +
            "uniform.float.worldValues = bedrockLevel + cloudHeight + heightLimit + logicalHeightLimit + hasCeiling + hasSkylight + ambientLight\n" +
            "uniform.float.miscValues = currentColorSpace + playerMood + maxBlindnessDarkness\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertEquals(0.0, manager.evaluateUniformForTesting("playerFlags"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("firstPerson"), 0.0);
        assertEquals(15.0, manager.evaluateUniformForTesting("playerValues"), 0.0);
        assertEquals(-2.0, manager.evaluateUniformForTesting("handValues"), 0.0);
        assertEquals(705.0, manager.evaluateUniformForTesting("worldValues"), 0.0);
        assertEquals(0.0, manager.evaluateUniformForTesting("miscValues"), 0.0);
    }

    @Test
    public void heldBlockLightCustomExpressionCapturesOldHandLightDirective() throws Exception {
        Object2IntOpenHashMap<NamespacedId> itemIds = new Object2IntOpenHashMap<>();
        itemIds.defaultReturnValue(-1);
        ShaderProperties oldHandLightDisabled = new ShaderProperties(
            "oldHandLight=false\n" +
            "uniform.float.handLight = heldBlockLightValue\n"
        );
        ShaderProperties oldHandLightEnabled = new ShaderProperties(
            "oldHandLight=true\n" +
            "uniform.float.handLight = heldBlockLightValue\n"
        );
        CustomUniformExpressionManager disabledManager =
            CustomUniformExpressionManager.fromProperties(oldHandLightDisabled, itemIds);
        CustomUniformExpressionManager enabledManager =
            CustomUniformExpressionManager.fromProperties(oldHandLightEnabled, itemIds);

        assertFalse(disabledManager.isOldHandLightForTesting());
        assertTrue(enabledManager.isOldHandLightForTesting());
        assertSame(itemIds, disabledManager.itemIdMapForTesting());
        assertSame(itemIds, enabledManager.itemIdMapForTesting());

        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/uniforms/custom/CustomUniformExpressionManager.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("IdMapUniforms.getHeldBlockLightValueMain(oldHandLight)"));
        assertTrue(source.contains("IdMapUniforms.getHeldItemIdMain(itemIdMap)"));
        assertTrue(source.contains("IdMapUniforms.getHeldItemIdOff(itemIdMap)"));
        assertTrue(source.contains("IdMapUniforms.getCurrentRenderedItemId(itemIdMap)"));
        assertFalse(source.contains("case \"heldBlockLightValue\":\n"
            + "                    return (double) IdMapUniforms.getHeldBlockLightValueMain();"));
    }

    @Test
    public void customExpressionsCanReadFrameStableFallbackSymbols() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.frameFallbacks = framemod2 + framemod4 + framemod8 + frame_mod + dither_shift\n" +
            "uniform.float.viewportFallbacks = pixel_size_x + pixel_size_y + inv_aspect_ratio\n" +
            "uniform.float.timeFallbacks = tickDelta + day_moment + day_mixer + night_mixer + vol_mixer + light_mix\n" +
            "uniform.vec2.jitterFallback = taa_offset\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        for (int i = 0; i < 19; i++) {
            SystemTimeUniforms.COUNTER.beginFrame();
        }
        manager.beginFrame();

        assertEquals(10.625, manager.evaluateUniformForTesting("frameFallbacks"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("viewportFallbacks"), 0.0);
        assertEquals(2.0, manager.evaluateUniformForTesting("timeFallbacks"), 0.0);

        float[] jitter = manager.evaluateUniformVectorForTesting("jitterFallback", 2);
        assertEquals(0.0F, jitter[0], 0.0F);
        assertEquals(0.0F, jitter[1], 0.0F);
    }

    @Test
    public void customUniformDirectivesOverrideHardcodedFallbackSymbols() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.day_moment = 7\n" +
            "uniform.float.derived = day_moment + 1\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertEquals(8.0, manager.evaluateUniformForTesting("derived"), 0.0);
    }

    @Test
    public void customExpressionDirectiveTypesEvaluateBooleanIntegerAndVectorVariables() {
        ShaderProperties properties = new ShaderProperties(
            "variable.bool.shouldAdd = true\n" +
            "variable.int.two = 2\n" +
            "variable.vec4.vector = vec4(two, if(shouldAdd, 3, 0), 4, 5)\n" +
            "uniform.bool.flag = shouldAdd && vector.y == 3\n" +
            "uniform.int.whole = two + 4\n" +
            "uniform.vec4.outVec = vector + vec4(1, 1, 1, 1)\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertTrue(manager.hasUniform("flag"));
        assertEquals(1.0, manager.evaluateUniformForTesting("flag"), 0.0);
        assertEquals(6.0, manager.evaluateUniformForTesting("whole"), 0.0);

        float[] outVec = manager.evaluateUniformVectorForTesting("outVec", 4);
        assertEquals(3.0F, outVec[0], 0.0F);
        assertEquals(4.0F, outVec[1], 0.0F);
        assertEquals(5.0F, outVec[2], 0.0F);
        assertEquals(6.0F, outVec[3], 0.0F);
    }

    @Test
    public void customExpressionsCanReadFixedVectorUniformSymbols() {
        BuiltinReplacementUniforms.setColorModulator(0.25F, 0.5F, 0.75F, 1.0F);
        CapturedRenderingState.INSTANCE.getCameraPosition()[0] = 11.0;
        CapturedRenderingState.INSTANCE.getCameraPosition()[1] = 13.0;
        CapturedRenderingState.INSTANCE.getCameraPosition()[2] = 17.0;
        CapturedRenderingState.INSTANCE.getCameraPositionVec()[0] = 11.0F;
        CapturedRenderingState.INSTANCE.getCameraPositionVec()[1] = 13.0F;
        CapturedRenderingState.INSTANCE.getCameraPositionVec()[2] = 17.0F;
        CapturedRenderingState.INSTANCE.getCameraPositionInt()[0] = 11;
        CapturedRenderingState.INSTANCE.getCameraPositionInt()[1] = 13;
        CapturedRenderingState.INSTANCE.getCameraPositionInt()[2] = 17;
        CapturedRenderingState.INSTANCE.getCameraPositionFract()[0] = 0.25F;
        CapturedRenderingState.INSTANCE.getCameraPositionFract()[1] = 0.5F;
        CapturedRenderingState.INSTANCE.getCameraPositionFract()[2] = 0.75F;
        CapturedRenderingState.INSTANCE.getPreviousCameraPosition()[0] = 1.0;
        CapturedRenderingState.INSTANCE.getPreviousCameraPosition()[1] = 2.0;
        CapturedRenderingState.INSTANCE.getPreviousCameraPosition()[2] = 3.0;
        CapturedRenderingState.INSTANCE.getPreviousCameraPositionInt()[0] = 1;
        CapturedRenderingState.INSTANCE.getPreviousCameraPositionInt()[1] = 2;
        CapturedRenderingState.INSTANCE.getPreviousCameraPositionInt()[2] = 3;
        CapturedRenderingState.INSTANCE.getPreviousCameraPositionFract()[0] = 0.125F;
        CapturedRenderingState.INSTANCE.getPreviousCameraPositionFract()[1] = 0.25F;
        CapturedRenderingState.INSTANCE.getPreviousCameraPositionFract()[2] = 0.5F;

        ShaderProperties properties = new ShaderProperties(
            "uniform.vec3.cameraDelta = cameraPosition - previousCameraPosition\n" +
            "uniform.vec3.cameraVectors = eyePosition + relativeEyePosition + playerLookVector + playerBodyVector\n" +
            "uniform.vec3.cameraAliases = u_CameraPosition + iris_CameraTranslation\n" +
            "uniform.vec3.cameraSplit = cameraPositionInt + cameraPositionFract + previousCameraPositionInt + previousCameraPositionFract\n" +
            "uniform.vec3.celestialVectors = sunPosition + moonPosition + shadowLightPosition + upPosition\n" +
            "uniform.vec3.modelScale = u_ModelScale\n" +
            "uniform.vec2.textureScale = u_TextureScale + iris_ScreenSize\n" +
            "uniform.vec3.chunkOffset = iris_ChunkOffset\n" +
            "uniform.vec4.colorModulator = iris_ColorModulator\n" +
            "uniform.float.lightningVisible = lightningBoltPosition.w\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        float[] cameraDelta = manager.evaluateUniformVectorForTesting("cameraDelta", 3);
        assertEquals(10.0F, cameraDelta[0], 0.0F);
        assertEquals(11.0F, cameraDelta[1], 0.0F);
        assertEquals(14.0F, cameraDelta[2], 0.0F);

        float[] cameraVectors = manager.evaluateUniformVectorForTesting("cameraVectors", 3);
        assertEquals(0.0F, cameraVectors[0], 0.0F);
        assertEquals(0.0F, cameraVectors[1], 0.0F);
        assertEquals(0.0F, cameraVectors[2], 0.0F);
        assertEquals(0.0, manager.evaluateUniformForTesting("lightningVisible"), 0.0);

        float[] cameraAliases = manager.evaluateUniformVectorForTesting("cameraAliases", 3);
        assertEquals(22.0F, cameraAliases[0], 0.0F);
        assertEquals(26.0F, cameraAliases[1], 0.0F);
        assertEquals(34.0F, cameraAliases[2], 0.0F);

        float[] cameraSplit = manager.evaluateUniformVectorForTesting("cameraSplit", 3);
        assertEquals(12.375F, cameraSplit[0], 0.0F);
        assertEquals(15.75F, cameraSplit[1], 0.0F);
        assertEquals(21.25F, cameraSplit[2], 0.0F);

        float[] celestialVectors = manager.evaluateUniformVectorForTesting("celestialVectors", 3);
        assertEquals(0.0F, celestialVectors[0], 0.0F);
        assertEquals(200.0F, celestialVectors[1], 0.0F);
        assertEquals(0.0F, celestialVectors[2], 0.0F);

        float[] modelScale = manager.evaluateUniformVectorForTesting("modelScale", 3);
        assertEquals(GameplayUniforms.getTerrainModelScale(), modelScale[0], 0.0F);
        assertEquals(GameplayUniforms.getTerrainModelScale(), modelScale[1], 0.0F);
        assertEquals(GameplayUniforms.getTerrainModelScale(), modelScale[2], 0.0F);

        float[] textureScale = manager.evaluateUniformVectorForTesting("textureScale", 2);
        assertEquals(GameplayUniforms.getTerrainTextureScale(), textureScale[0], 0.0F);
        assertEquals(GameplayUniforms.getTerrainTextureScale(), textureScale[1], 0.0F);

        float[] chunkOffset = manager.evaluateUniformVectorForTesting("chunkOffset", 3);
        assertEquals(0.0F, chunkOffset[0], 0.0F);
        assertEquals(0.0F, chunkOffset[1], 0.0F);
        assertEquals(0.0F, chunkOffset[2], 0.0F);

        float[] colorModulator = manager.evaluateUniformVectorForTesting("colorModulator", 4);
        assertTrue(manager.isUniformDynamicForTesting("colorModulator"));
        assertEquals(0.25F, colorModulator[0], 0.0F);
        assertEquals(0.5F, colorModulator[1], 0.0F);
        assertEquals(0.75F, colorModulator[2], 0.0F);
        assertEquals(1.0F, colorModulator[3], 0.0F);
    }

    @Test
    public void smoothSupportsOmittedIdSyntax() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.omitted = smooth(frameCounter > 1, 2, 2)\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(0L);
        manager.beginFrame();
        assertEquals(0.0, manager.evaluateUniformForTesting("omitted"), 0.0);

        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(1_000_000_000L);
        manager.beginFrame();

        double smoothed = manager.evaluateUniformForTesting("omitted");
        assertTrue(smoothed > 0.0);
        assertTrue(smoothed < 1.0);
    }

    @Test
    public void duplicateExplicitSmoothIdsRemainExpressionLocalForTargetPackCompatibility() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.first = smooth(4, 0, 5, 5)\n" +
            "uniform.float.second = smooth(4, 1, 5, 5)\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(0L);
        manager.beginFrame();
        assertEquals(0.0, manager.evaluateUniformForTesting("first"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("second"), 0.0);

        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(1_000_000_000L);
        manager.beginFrame();
        assertEquals(0.0, manager.evaluateUniformForTesting("first"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("second"), 0.0);
    }

    @Test
    public void vectorCustomUniformsEvaluateMakeupStyleTaaOffset() {
        ShaderProperties properties = new ShaderProperties(
            "variable.float.frame_mod = fmod(frameCounter, 16)\n" +
            "uniform.vec2.taa_offset = vec2(if(frame_mod == 3, 0.5, 0.0), if(frame_mod == 3, -0.5, 0.0))\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        for (int i = 0; i < 19; i++) {
            SystemTimeUniforms.COUNTER.beginFrame();
        }
        manager.beginFrame();

        float[] offset = manager.evaluateUniformVectorForTesting("taa_offset", 2);
        assertEquals(0.5F, offset[0], 0.0F);
        assertEquals(-0.5F, offset[1], 0.0F);
    }

    @Test
    public void vectorVariablesSupportComponentAccessAndVectorMath() {
        ShaderProperties properties = new ShaderProperties(
            "variable.vec3.base = vec3(1, 2, 3)\n" +
            "uniform.float.middle = base.y\n" +
            "uniform.vec3.scaled = base * 2\n" +
            "uniform.vec3.clamped = clamp(vec3(-1, 0.5, 2), 0, 1)\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertEquals(2.0, manager.evaluateUniformForTesting("middle"), 0.0);

        float[] scaled = manager.evaluateUniformVectorForTesting("scaled", 3);
        assertEquals(2.0F, scaled[0], 0.0F);
        assertEquals(4.0F, scaled[1], 0.0F);
        assertEquals(6.0F, scaled[2], 0.0F);

        float[] clamped = manager.evaluateUniformVectorForTesting("clamped", 3);
        assertEquals(0.0F, clamped[0], 0.0F);
        assertEquals(0.5F, clamped[1], 0.0F);
        assertEquals(1.0F, clamped[2], 0.0F);
    }

    @Test
    public void dynamicCustomUniformsReadObjectStateWithinTheSameFrame() {
        ShaderProperties properties = new ShaderProperties(
            "uniform.float.activeEntity = entityId + 1\n" +
            "variable.float.blockPart = blockEntityId + 2\n" +
            "uniform.float.activeBlock = blockPart\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        assertTrue(manager.isUniformDynamicForTesting("activeEntity"));
        assertTrue(manager.isUniformDynamicForTesting("activeBlock"));
        assertEquals(0.0, manager.evaluateUniformForTesting("activeEntity"), 0.0);
        assertEquals(1.0, manager.evaluateUniformForTesting("activeBlock"), 0.0);

        CapturedRenderingState.INSTANCE.setCurrentEntity(42);
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(8);

        assertEquals(43.0, manager.evaluateUniformForTesting("activeEntity"), 0.0);
        assertEquals(10.0, manager.evaluateUniformForTesting("activeBlock"), 0.0);
    }

    @Test
    public void dynamicClassificationHonorsCustomOverridesOfBuiltinNames() {
        ShaderProperties properties = new ShaderProperties(
            "variable.float.entityId = 5\n" +
            "uniform.float.activeEntity = entityId + 1\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();
        CapturedRenderingState.INSTANCE.setCurrentEntity(42);

        assertFalse(manager.isUniformDynamicForTesting("activeEntity"));
        assertEquals(6.0, manager.evaluateUniformForTesting("activeEntity"), 0.0);
    }

    @Test
    public void dynamicCustomUniformsTrackTransitiveRuntimeDependencies() {
        ShaderProperties properties = new ShaderProperties(
            "variable.float.passObject = renderStage + entityId + blockEntityId + currentRenderedItemId\n" +
            "uniform.float.dynamicUniform = passObject + fogMode + fogDensity + fogStart + fogEnd + iris_FogStart + iris_FogEnd + iris_ColorModulator.x + entityColor.x + iris_entityColor.y + atlasSize.x + gtextureSize.x + blendFunc.x\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        assertTrue(manager.isUniformDynamicForTesting("dynamicUniform"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("renderStage"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("entityId"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("blockEntityId"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("currentRenderedItemId"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("fogMode"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("fogDensity"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("fogStart"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("fogEnd"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("iris_FogStart"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("iris_FogEnd"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("iris_ColorModulator"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("entityColor"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("iris_entityColor"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("atlasSize"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("gtextureSize"));
        assertTrue(manager.dynamicDependenciesForTesting("dynamicUniform").contains("blendFunc"));
    }

    @Test
    public void runtimeVectorCustomExpressionsUseRuntimeNotifiers() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/uniforms/custom/CustomUniformExpressionManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("case \"iris_ColorModulator\":\n"
            + "                    return BuiltinReplacementUniforms.getColorModulatorNotifier();"));
        assertTrue(source.contains("case \"entityColor\":\n"
            + "                case \"iris_entityColor\":\n"
            + "                    return GameplayUniforms.getEntityColorNotifier();"));
    }

    @Test
    public void combinedDynamicNotifierAttemptsEveryDelegateRemovalBeforeRethrowing() throws Exception {
        final int[] removed = new int[3];
        RuntimeException firstFailure = new RuntimeException("first");
        RuntimeException secondFailure = new RuntimeException("second");
        Runnable listener = () -> { };
        ValueUpdateNotifier notifier = newCombinedNotifier(
            throwingRemoveNotifier(removed, 0, firstFailure),
            throwingRemoveNotifier(removed, 1, secondFailure),
            countingNotifier(removed, 2));

        try {
            notifier.removeListener(listener);
        } catch (RuntimeException exception) {
            assertEquals("first", exception.getMessage());
            assertEquals(1, exception.getSuppressed().length);
            assertEquals("second", exception.getSuppressed()[0].getMessage());
            assertEquals(1, removed[0]);
            assertEquals(1, removed[1]);
            assertEquals(1, removed[2]);
            return;
        }

        throw new AssertionError("Expected combined notifier removal to rethrow the first failure");
    }

    @Test
    public void combinedDynamicNotifierKeepsOriginalFailureWhenDelegatesShareThrowableInstance() throws Exception {
        final int[] removed = new int[2];
        RuntimeException sharedFailure = new RuntimeException("shared");
        Runnable listener = () -> { };
        ValueUpdateNotifier notifier = newCombinedNotifier(
            throwingRemoveNotifier(removed, 0, sharedFailure),
            throwingRemoveNotifier(removed, 1, sharedFailure));

        try {
            notifier.removeListener(listener);
        } catch (RuntimeException exception) {
            assertSame(sharedFailure, exception);
            assertEquals(0, exception.getSuppressed().length);
            assertEquals(1, removed[0]);
            assertEquals(1, removed[1]);
            return;
        }

        throw new AssertionError("Expected combined notifier removal to rethrow the shared failure");
    }

    @Test
    public void dynamicVectorCustomUniformsReadFogColorWithinTheSameFrame() {
        CapturedRenderingState.INSTANCE.setFogColor(0.1F, 0.2F, 0.3F);
        ShaderProperties properties = new ShaderProperties(
            "uniform.vec3.liveFog = fogColor\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        float[] first = manager.evaluateUniformVectorForTesting("liveFog", 3);
        assertTrue(manager.isUniformDynamicForTesting("liveFog"));
        assertEquals(0.1F, first[0], 0.0F);
        assertEquals(0.2F, first[1], 0.0F);
        assertEquals(0.3F, first[2], 0.0F);

        CapturedRenderingState.INSTANCE.setFogColor(0.4F, 0.5F, 0.6F);

        float[] second = manager.evaluateUniformVectorForTesting("liveFog", 3);
        assertEquals(0.4F, second[0], 0.0F);
        assertEquals(0.5F, second[1], 0.0F);
        assertEquals(0.6F, second[2], 0.0F);
    }

    @Test
    public void dynamicVectorCustomUniformsReadEntityColorWithinTheSameFrame() {
        GameplayUniforms.setEntityColor(0.1F, 0.2F, 0.3F, 0.4F);
        ShaderProperties properties = new ShaderProperties(
            "uniform.vec4.liveEntityColor = iris_entityColor\n"
        );
        CustomUniformExpressionManager manager = CustomUniformExpressionManager.fromProperties(properties);

        manager.beginFrame();

        float[] first = manager.evaluateUniformVectorForTesting("liveEntityColor", 4);
        assertTrue(manager.isUniformDynamicForTesting("liveEntityColor"));
        assertEquals(0.1F, first[0], 0.0F);
        assertEquals(0.2F, first[1], 0.0F);
        assertEquals(0.3F, first[2], 0.0F);
        assertEquals(0.4F, first[3], 0.0F);

        GameplayUniforms.setEntityColor(0.5F, 0.6F, 0.7F, 0.8F);

        float[] second = manager.evaluateUniformVectorForTesting("liveEntityColor", 4);
        assertEquals(0.5F, second[0], 0.0F);
        assertEquals(0.6F, second[1], 0.0F);
        assertEquals(0.7F, second[2], 0.0F);
        assertEquals(0.8F, second[3], 0.0F);
    }

    @Test
    public void matrixAliasUsesNormalMatrixCapture() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/uniforms/custom/CustomUniformExpressionManager.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("case \"iris_NormalMatrix\":\n"
            + "                    return CapturedRenderingState.INSTANCE.getNormalMatrix();"));
        assertFalse(source.contains("case \"iris_NormalMatrix\":\n"
            + "                    return CapturedRenderingState.INSTANCE.getModelViewInverse();"));
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static void resetCapturedVectors() {
        double[] cameraPosition = CapturedRenderingState.INSTANCE.getCameraPosition();
        double[] previousCameraPosition = CapturedRenderingState.INSTANCE.getPreviousCameraPosition();
        float[] cameraPositionVec = CapturedRenderingState.INSTANCE.getCameraPositionVec();
        float[] previousCameraPositionVec = CapturedRenderingState.INSTANCE.getPreviousCameraPositionVec();
        int[] cameraPositionInt = CapturedRenderingState.INSTANCE.getCameraPositionInt();
        int[] previousCameraPositionInt = CapturedRenderingState.INSTANCE.getPreviousCameraPositionInt();
        float[] cameraPositionFract = CapturedRenderingState.INSTANCE.getCameraPositionFract();
        float[] previousCameraPositionFract = CapturedRenderingState.INSTANCE.getPreviousCameraPositionFract();
        for (int i = 0; i < cameraPosition.length; i++) {
            cameraPosition[i] = 0.0;
            previousCameraPosition[i] = 0.0;
            cameraPositionVec[i] = 0.0F;
            previousCameraPositionVec[i] = 0.0F;
            cameraPositionInt[i] = 0;
            previousCameraPositionInt[i] = 0;
            cameraPositionFract[i] = 0.0F;
            previousCameraPositionFract[i] = 0.0F;
        }
        CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(-1);
        CapturedRenderingState.INSTANCE.setFogColor(0.0F, 0.0F, 0.0F);
    }

    private static ValueUpdateNotifier newCombinedNotifier(ValueUpdateNotifier... notifiers) throws Exception {
        Class<?> combinedClass = Class.forName(
            "net.oculus.uniforms.custom.CustomUniformExpressionManager$CombinedValueUpdateNotifier");
        Constructor<?> constructor = combinedClass.getDeclaredConstructor(java.util.List.class);
        constructor.setAccessible(true);
        return (ValueUpdateNotifier) constructor.newInstance(Arrays.asList(notifiers));
    }

    private static ValueUpdateNotifier throwingRemoveNotifier(int[] removed, int index, RuntimeException failure) {
        return new ValueUpdateNotifier() {
            @Override
            public void setListener(Runnable listener) {
            }

            @Override
            public void removeListener(Runnable listener) {
                removed[index]++;
                throw failure;
            }
        };
    }

    private static ValueUpdateNotifier countingNotifier(int[] removed, int index) {
        return new ValueUpdateNotifier() {
            @Override
            public void setListener(Runnable listener) {
            }

            @Override
            public void removeListener(Runnable listener) {
                removed[index]++;
            }
        };
    }
}

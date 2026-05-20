package net.oculus.gl.program;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;

public class ProgramBuilderReferenceUniformCoverageTest {
    private static final Pattern DIRECT_REFERENCE_UNIFORM_PATTERN = Pattern.compile(
        "\\.(?:uniform\\w+|externallyManagedUniform)\\s*\\([^\\n;]*?\"([A-Za-z0-9_]+)\"");
    private static final Pattern EXTERNALLY_MANAGED_HELPER_PATTERN = Pattern.compile(
        "\\b(?:addMat4|addVec4|addFloat)\\s*\\(\\s*uniformHolder\\s*,\\s*\"([A-Za-z0-9_]+)\"");

    private static final String[] REFERENCE_1_16_5_UNIFORM_SOURCE_FILES = {
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/CommonUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/CameraUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/ViewportUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/WorldTimeUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/SystemTimeUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/CelestialUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/IdMapUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/HardcodedCustomUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/IrisExclusiveUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/FogUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/ExternallyManagedUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/MatrixUniforms.java",
        "../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/builtin/BuiltinReplacementUniforms.java"
    };

    private static final String[] REFERENCE_1_16_5_UNIFORMS = {
        "BiomeTemp",
        "ambientLight",
        "aspectRatio",
        "atlasSize",
        "bedrockLevel",
        "blendFunc",
        "blindFactor",
        "blindness",
        "blockEntityId",
        "burningSmooth",
        "cameraPosition",
        "cloudHeight",
        "currentColorSpace",
        "currentPlayerAir",
        "currentPlayerHealth",
        "currentPlayerHunger",
        "dawnDusk",
        "day",
        "darknessFactor",
        "darknessLightFactor",
        "effectStrength",
        "entityColor",
        "entityId",
        "eyeAltitude",
        "eyeBrightness",
        "eyeBrightnessM",
        "eyeBrightnessSmooth",
        "eyePosition",
        "far",
        "firstPersonCamera",
        "fogColor",
        "fogDensity",
        "fogEnd",
        "fogMode",
        "fogStart",
        "frameCounter",
        "frameTime",
        "frameTimeCounter",
        "frameTimeSmooth",
        "framemod8",
        "gbufferModelView",
        "gbufferModelViewInverse",
        "gbufferPreviousModelView",
        "gbufferPreviousProjection",
        "gbufferProjection",
        "gbufferProjectionInverse",
        "gtextureSize",
        "hasCeiling",
        "hasSkylight",
        "heightLimit",
        "heldBlockLightValue",
        "heldBlockLightValue2",
        "heldItemId",
        "heldItemId2",
        "hideGUI",
        "inSwamp",
        "iris_CameraTranslation",
        "iris_ChunkOffset",
        "iris_ColorModulator",
        "iris_FogColor",
        "iris_FogDensity",
        "iris_FogEnd",
        "iris_FogStart",
        "iris_LightmapTextureMatrix",
        "iris_LineWidth",
        "iris_ModelOffset",
        "iris_ModelViewMat",
        "iris_ModelViewMatrix",
        "iris_ModelViewProjectionMatrix",
        "iris_NormalMatrix",
        "iris_ProjectionMatrix",
        "iris_ProjMat",
        "iris_ScreenSize",
        "iris_TextureMat",
        "isDry",
        "isEyeInCave",
        "isEyeInWater",
        "isPrecipitationRain",
        "isRainy",
        "isSnowy",
        "isSpectator",
        "is_burning",
        "is_hurt",
        "is_invisible",
        "is_on_ground",
        "is_sneaking",
        "is_sprinting",
        "lightningBoltPosition",
        "logicalHeightLimit",
        "maxPlayerAir",
        "maxPlayerHealth",
        "maxPlayerHunger",
        "moonBrightness",
        "moonPhase",
        "moonPosition",
        "near",
        "night",
        "nightVision",
        "pi",
        "playerBodyVector",
        "playerLookVector",
        "playerMood",
        "previousCameraPosition",
        "rainFactor",
        "rainStrength",
        "rainStrengthS",
        "rainStrengthS2",
        "rainStrengthShiningStars",
        "relativeEyePosition",
        "renderStage",
        "screenBrightness",
        "shadowAngle",
        "shadowFade",
        "shadowLightPosition",
        "shadowModelView",
        "shadowModelViewInverse",
        "shadowProjection",
        "shadowProjectionInverse",
        "shdFade",
        "skyColor",
        "sneakSmooth",
        "starter",
        "sunAngle",
        "sunPosition",
        "thunderStrength",
        "timeAngle",
        "timeBrightness",
        "touchmybody",
        "u_ModelScale",
        "u_ModelViewProjectionMatrix",
        "u_TextureScale",
        "upPosition",
        "velocity",
        "viewHeight",
        "viewWidth",
        "wetness",
        "worldDay",
        "worldTime"
    };

    private static final String[] REFERENCE_1_16_5_GENERATED_MATRIX_UNIFORMS = {
        "gbufferModelView",
        "gbufferModelViewInverse",
        "gbufferPreviousModelView",
        "gbufferProjection",
        "gbufferProjectionInverse",
        "gbufferPreviousProjection",
        "shadowModelView",
        "shadowModelViewInverse",
        "shadowProjection",
        "shadowProjectionInverse"
    };

    private static final String[] COMMENTED_REFERENCE_ID_MAP_UNIFORMS = {
        "heldBlockLightColor",
        "heldBlockLightColor2"
    };

    private static final String[] COMPLEMENTARY_TARGET_RUNTIME_UNIFORMS = {
        "cameraPositionInt",
        "previousCameraPositionInt",
        "cameraPositionFract",
        "previousCameraPositionFract",
        "framemod2",
        "framemod4",
        "heavyFog",
        "maxBlindnessDarkness"
    };

    private static final String[] MAKEUP_TARGET_RUNTIME_UNIFORMS = {
        "pixel_size_x",
        "pixel_size_y",
        "inv_aspect_ratio",
        "day_moment",
        "day_mixer",
        "night_mixer",
        "vol_mixer",
        "light_mix",
        "frame_mod",
        "taa_offset",
        "dither_shift",
        "fov_y_inv"
    };

    @Test
    public void programBuilderKeepsActiveReferenceUniformSurfaceReachable() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        for (String uniformName : REFERENCE_1_16_5_UNIFORMS) {
            assertTrue("Missing ProgramBuilder case for " + uniformName,
                source.contains("case \"" + uniformName + "\":"));
        }
    }

    @Test
    public void referenceUniformCoverageListTracksActiveReferenceSourceLiterals() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        Set<String> referenceUniforms = deriveActiveReferenceUniformNames();

        assertFalse("Reference uniform source scan returned no names", referenceUniforms.isEmpty());

        for (String uniformName : referenceUniforms) {
            assertContainsRequiredReferenceUniform(uniformName);
            assertTrue("Missing ProgramBuilder case for active reference source uniform " + uniformName,
                builder.contains("case \"" + uniformName + "\":"));
        }
    }

    @Test
    public void referenceCoverageTracksGeneratedMatrixAliasesAndSkipsInactiveHeldLightColorTodo() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String matrices = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/MatrixUniforms.java");
        String idMap = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/IdMapUniforms.java");

        assertTrue(matrices.contains("addMatrix(uniforms, \"ModelView\","));
        assertTrue(matrices.contains("addMatrix(uniforms, \"Projection\","));
        assertTrue(matrices.contains("addShadowMatrix(uniforms, \"ModelView\","));
        assertTrue(matrices.contains("addShadowArrayMatrix(uniforms, \"Projection\","));
        assertTrue(matrices.contains("\"gbuffer\" + name"));
        assertTrue(matrices.contains("\"gbuffer\" + name + \"Inverse\""));
        assertTrue(matrices.contains("\"gbufferPrevious\" + name"));
        assertTrue(matrices.contains("\"shadow\" + name"));
        assertTrue(matrices.contains("\"shadow\" + name + \"Inverse\""));

        for (String uniformName : REFERENCE_1_16_5_GENERATED_MATRIX_UNIFORMS) {
            assertContainsRequiredReferenceUniform(uniformName);
            assertTrue("Missing ProgramBuilder case for generated reference matrix uniform " + uniformName,
                builder.contains("case \"" + uniformName + "\":"));
        }

        for (String uniformName : COMMENTED_REFERENCE_ID_MAP_UNIFORMS) {
            assertTrue("Expected held-light color to remain a commented reference TODO",
                idMap.contains("//.uniformVanilla3f(PER_FRAME, \"" + uniformName + "\""));
            assertFalse("Inactive commented reference TODO should not be required: " + uniformName,
                containsReferenceUniform(uniformName));
            assertFalse("Inactive commented reference TODO should not be registered: " + uniformName,
                builder.contains("case \"" + uniformName + "\":"));
        }
    }

    @Test
    public void programBuilderKeepsComplementaryTargetRuntimeUniformsReachable() throws IOException {
        String complementaryUniformsPath =
            "run/shaderpacks/ComplementaryReimagined_r5.6.1/shaders/lib/uniforms.glsl";
        Assume.assumeTrue("Complementary Reimagined shader pack is not available",
            Files.isRegularFile(Paths.get(complementaryUniformsPath)));

        String complementaryUniforms = read(complementaryUniformsPath);
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        for (String uniformName : COMPLEMENTARY_TARGET_RUNTIME_UNIFORMS) {
            assertTrue("Complementary target pack no longer declares " + uniformName,
                complementaryUniforms.contains(uniformName));
            assertTrue("Missing ProgramBuilder case for Complementary target uniform " + uniformName,
                builder.contains("case \"" + uniformName + "\":"));
        }
    }

    @Test
    public void programBuilderKeepsMakeUpTargetRuntimeUniformsReachable() throws IOException {
        String makeUpPropertiesPath =
            "run/shaderpacks/MakeUp-UltraFast-9.3e/shaders/shaders.properties";
        Assume.assumeTrue("MakeUp Ultra Fast shader pack is not available",
            Files.isRegularFile(Paths.get(makeUpPropertiesPath)));

        String makeUpProperties = read(makeUpPropertiesPath);
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        for (String uniformName : MAKEUP_TARGET_RUNTIME_UNIFORMS) {
            assertTrue("MakeUp target pack no longer declares " + uniformName,
                makeUpProperties.contains("uniform.float." + uniformName)
                    || makeUpProperties.contains("uniform.int." + uniformName)
                    || makeUpProperties.contains("uniform.vec2." + uniformName));
            assertTrue("Missing ProgramBuilder case for MakeUp target uniform " + uniformName,
                builder.contains("case \"" + uniformName + "\":"));
        }
    }

    @Test
    public void programBuilderBinds117ExternallyManagedUniformsToBackportSuppliers() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String matrixState = read("src/main/java/net/oculus/gl/state/MatrixState.java");

        assertTrue(builder.contains("case \"iris_FogStart\":\n"
            + "                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogStart()"));
        assertTrue(builder.contains("case \"iris_FogEnd\":\n"
            + "                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogEnd()"));
        assertTrue(builder.contains("case \"iris_TextureMat\":\n"
            + "                uniforms.addMat4f(uniformName, MatrixState::updateTextureMatrix);"));
        assertTrue(builder.contains("case \"iris_ModelViewMat\":\n"
            + "                uniforms.addMat4f(uniformName, MatrixState::updateModelViewMatrix);"));
        assertTrue(builder.contains("case \"iris_ProjMat\":\n"
            + "                uniforms.addMat4f(uniformName, MatrixState::updateProjectionMatrix);"));
        assertTrue(builder.contains("case \"iris_ChunkOffset\":\n"
            + "                uniforms.addVec3(uniformName, BuiltinReplacementUniforms::getChunkOffset);"));
        assertTrue(builder.contains("case \"iris_ColorModulator\":\n"
            + "                uniforms.addVec4(uniformName, BuiltinReplacementUniforms::getColorModulator,\n"
            + "                    BuiltinReplacementUniforms.getColorModulatorNotifier());"));
        assertTrue(matrixState.contains("GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, TEXTURE);"));
    }

    @Test
    public void glStateManagerMixinTracksColorModulatorFor117Uniform() throws IOException {
        String source = read("src/main/java/net/oculus/mixin/pipeline/GlStateManagerStateMixin.java");

        assertTrue(source.contains("@Inject(method = \"color(FFFF)V\", at = @At(\"RETURN\"))"));
        assertTrue(source.contains("BuiltinReplacementUniforms.setColorModulator(red, green, blue, alpha);"));
        assertTrue(source.contains("@Inject(method = \"color(FFF)V\", at = @At(\"RETURN\"))"));
        assertTrue(source.contains("BuiltinReplacementUniforms.setColorModulator(red, green, blue, 1.0F);"));
    }

    @Test
    public void heldBlockLightValueCapturesOldHandLightPackDirectiveLikeReference() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/uniforms/IdMapUniforms.java");
        String customUniforms = read("src/main/java/net/oculus/uniforms/custom/CustomUniformExpressionManager.java");
        String pipeline = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");

        assertTrue(reference.contains(
            "new HeldItemSupplier(InteractionHand.MAIN_HAND, idMap.getItemIdMap(), isOldHandLight)"));
        assertTrue(reference.contains(
            "new HeldItemSupplier(InteractionHand.OFF_HAND, idMap.getItemIdMap(), false)"));
        assertTrue(builder.contains("case \"heldItemId\":\n"
            + "                uniforms.addInt(uniformName, customUniforms::getHeldItemIdMain);"));
        assertTrue(builder.contains("case \"heldItemId2\":\n"
            + "                uniforms.addInt(uniformName, customUniforms::getHeldItemIdOff);"));
        assertTrue(builder.contains("case \"currentRenderedItemId\":\n"
            + "                uniforms.addInt(uniformName, customUniforms::getCurrentRenderedItemId,"));
        assertTrue(builder.contains("case \"heldBlockLightValue\":\n"
            + "                uniforms.addInt(uniformName, () -> "
            + "IdMapUniforms.getHeldBlockLightValueMain(isOldHandLight()));"));
        assertTrue(builder.contains("case \"heldBlockLightValue2\":\n"
            + "                uniforms.addInt(uniformName, IdMapUniforms::getHeldBlockLightValueOff);"));
        assertTrue(builder.contains("private boolean isOldHandLight() {\n"
            + "        return packDirectives == null || packDirectives.isOldHandLight();\n"
            + "    }"));
        assertTrue(customUniforms.contains("fromShaderPack(ShaderPack pack)"));
        assertTrue(customUniforms.contains("pack.getIdMap().getItemIdMap()"));
        assertTrue(customUniforms.contains("case \"heldBlockLightValue2\":\n"
            + "                    return (double) IdMapUniforms.getHeldBlockLightValueOff();"));
        assertTrue(pipeline.contains("CustomUniformExpressionManager.fromShaderPack(pack)"));
    }

    @Test
    public void programBuilderClassifiesReferenceSamplerAndImageTypesAsManaged() throws Exception {
        assertTrue(invokeTypeClassifier("isSamplerType", GL20.GL_SAMPLER_1D));
        assertTrue(invokeTypeClassifier("isSamplerType", GL20.GL_SAMPLER_1D_SHADOW));
        assertTrue(invokeTypeClassifier("isSamplerType", GL20.GL_SAMPLER_2D));
        assertTrue(invokeTypeClassifier("isSamplerType", GL20.GL_SAMPLER_3D));
        assertTrue(invokeTypeClassifier("isSamplerType", GL20.GL_SAMPLER_CUBE));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_SAMPLER_1D_ARRAY));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_SAMPLER_2D_ARRAY));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_SAMPLER_2D_ARRAY_SHADOW));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_SAMPLER_BUFFER));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_INT_SAMPLER_1D));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_INT_SAMPLER_2D_ARRAY));
        assertTrue(invokeTypeClassifier("isSamplerType", GL30.GL_UNSIGNED_INT_SAMPLER_1D_ARRAY));
        assertTrue(invokeTypeClassifier("isSamplerType", GL31.GL_SAMPLER_2D_RECT));
        assertTrue(invokeTypeClassifier("isSamplerType", GL32.GL_SAMPLER_2D_MULTISAMPLE));
        assertTrue(invokeTypeClassifier("isSamplerType", GL32.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY));
        assertFalse(invokeTypeClassifier("isSamplerType", GL11.GL_FLOAT));

        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_1D));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_1D_ARRAY));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_2D_ARRAY));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_2D_RECT));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_CUBE));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_BUFFER));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_CUBE_MAP_ARRAY));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_IMAGE_2D_MULTISAMPLE));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_UNSIGNED_INT_IMAGE_2D));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_UNSIGNED_INT_IMAGE_BUFFER));
        assertTrue(invokeTypeClassifier("isImageType", GL42.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY));
        assertFalse(invokeTypeClassifier("isImageType", GL11.GL_FLOAT));
    }

    @Test
    public void owningLinkedProgramIsDeletedIfBuilderDiscoveryOrWrapperConstructionFails() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(builder.contains("return createOwningProgramBuilder(name, programId, overrides, customUniforms, reservedTextureUnits,\n"
            + "            discoverActiveUniforms, availability, frameUpdateNotifier, packDirectives);"));
        assertTrue(builder.contains("return createOwningProgramBuilder(name, programId, overrides, customUniforms, reservedTextureUnits,\n"
            + "            true, null, frameUpdateNotifier, packDirectives);"));
        assertTrue(builder.contains("catch (RuntimeException | Error exception) {\n"
            + "            deleteFailedProgramHandle(programId, name, exception, \"builder construction\");\n"
            + "            throw exception;\n"
            + "        }"));
        assertTrue(builder.contains("if (!ownsProgramHandle) {\n"
            + "            return;\n"
            + "        }\n"
            + "\n"
            + "        deleteFailedProgramHandle(program, name, failure, failurePhase);"));
        assertTrue(builder.contains("catch (RuntimeException | Error exception) {\n"
            + "            deleteFailedProgramAfterBuildFailure(exception, \"program wrapper construction\");\n"
            + "            throw exception;\n"
            + "        }"));
        assertTrue(builder.contains("catch (RuntimeException | Error exception) {\n"
            + "            deleteFailedProgramAfterBuildFailure(exception, \"compute wrapper construction\");\n"
            + "            throw exception;\n"
            + "        }"));
        assertTrue(builder.contains("OculusRenderSystem.glDeleteProgram(programId);"));
        assertTrue(builder.contains("if (cleanupFailure != null && cleanupFailure != failure) {\n"
            + "            failure.addSuppressed(cleanupFailure);\n"
            + "        }"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static boolean invokeTypeClassifier(String methodName, int glType) throws Exception {
        Method method = ProgramBuilder.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, glType);
    }

    private static void assertContainsRequiredReferenceUniform(String uniformName) {
        assertTrue("Coverage list missing required reference uniform " + uniformName,
            containsReferenceUniform(uniformName));
    }

    private static Set<String> deriveActiveReferenceUniformNames() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (String sourceFile : REFERENCE_1_16_5_UNIFORM_SOURCE_FILES) {
            String source = stripLineComments(read(sourceFile));
            collectReferenceUniformNames(source, DIRECT_REFERENCE_UNIFORM_PATTERN, names);
            collectReferenceUniformNames(source, EXTERNALLY_MANAGED_HELPER_PATTERN, names);
        }

        names.remove("gbuffer");
        names.remove("gbufferPrevious");
        names.remove("shadow");
        for (String uniformName : REFERENCE_1_16_5_GENERATED_MATRIX_UNIFORMS) {
            names.add(uniformName);
        }
        return names;
    }

    private static void collectReferenceUniformNames(String source, Pattern pattern, Set<String> names) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
    }

    private static String stripLineComments(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            int comment = line.indexOf("//");
            stripped.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return stripped.toString();
    }

    private static boolean containsReferenceUniform(String uniformName) {
        for (String referenceUniform : REFERENCE_1_16_5_UNIFORMS) {
            if (referenceUniform.equals(uniformName)) {
                return true;
            }
        }
        return false;
    }
}

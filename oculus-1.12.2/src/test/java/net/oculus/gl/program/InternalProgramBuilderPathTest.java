package net.oculus.gl.program;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import net.oculus.gl.OculusRenderSystem;
import org.junit.Test;

public class InternalProgramBuilderPathTest {
    @Test
    public void centerDepthUsesExplicitProgramBuilderPath() throws IOException {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");

        assertUsesExplicitProgramBuilder(source, "centerDepthSmooth");
        assertDoesNotUseAutomaticProgramBuilder(source, "centerDepthSmooth");
    }

    @Test
    public void colorSpaceFragmentUsesExplicitProgramBuilderPath() throws IOException {
        String source = read("src/main/java/net/oculus/colorspace/ColorSpaceFragmentConverter.java");

        assertUsesExplicitProgramBuilder(source, "colorSpaceFragment");
        assertDoesNotUseAutomaticProgramBuilder(source, "colorSpaceFragment");
    }

    @Test
    public void explicitProgramBuilderCanSkipAutomaticActiveUniformDiscovery() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("boolean discoverActiveUniforms"));
        assertTrue(source.contains("if (discoverActiveUniforms) {\n            discoverBuiltInUniforms();\n        }"));
        assertTrue(source.contains("public static ProgramBuilder beginExplicit("));
    }

    @Test
    public void computeBuilderFailsBeforeCompilingWhenComputeIsUnsupported() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        int supportCheck = source.indexOf("if (!OculusRenderSystem.supportsCompute())");
        int throwMessage = source.indexOf(
            "Compute shaders are not supported by the active OpenGL context", supportCheck);
        int programName = source.indexOf("but shader program \" + name + \" defines a compute pass.", throwMessage);
        int buildShader = source.indexOf("compute = buildShader(ShaderType.COMPUTE", supportCheck);

        assertTrue(supportCheck >= 0);
        assertTrue(throwMessage > supportCheck);
        assertTrue(programName > throwMessage);
        assertTrue(buildShader > programName);
    }

    @Test
    public void computeBuilderUnsupportedContextErrorNamesProgramBeforeGlWork() {
        String previous = System.getProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, "true");
        try {
            ProgramBuilder.beginCompute("shadowcomp", "#version 430\nvoid main() {}\n");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Compute shaders are not supported"));
            assertTrue(exception.getMessage().contains("shadowcomp"));
            assertTrue(exception.getMessage().contains("compute pass"));
            return;
        } finally {
            if (previous == null) {
                System.clearProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
            } else {
                System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, previous);
            }
        }

        throw new AssertionError("Expected unsupported compute context to fail before shader compilation");
    }

    @Test
    public void programBuilderCleansCompiledShadersOnProgramCreateFailure() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("GlShader vertex = null;"));
        assertTrue(source.contains("GlShader geometry = null;"));
        assertTrue(source.contains("GlShader fragment = null;"));
        assertTrue(source.contains("try {\n"
            + "            vertex = buildShader(ShaderType.VERTEX, name + \".vsh\", vertexSource);"));
        assertTrue(source.contains("} finally {\n"
            + "            destroyShader(vertex, name);\n"
            + "            destroyShader(geometry, name);\n"
            + "            destroyShader(fragment, name);\n"
            + "        }"));
        assertTrue(source.contains("GlShader compute = null;"));
        assertTrue(source.contains("} finally {\n"
            + "            destroyShader(compute, name);\n"
            + "        }"));
        assertTrue(source.contains("private static void destroyShader(GlShader shader, String programName)"));
        assertTrue(source.contains("} catch (RuntimeException | Error exception) {\n"
            + "            LOGGER.debug(\"Failed to destroy compiled shader object for program {}\", programName, exception);\n"
            + "        }"));
        assertFalse(source.contains("vertex.destroy();\n"
            + "        if (geometry != null)"));
        assertFalse(source.contains("int programId = ProgramCreator.create(name, compute);\n"
            + "        compute.destroy();"));
    }

    @Test
    public void glShaderConstructionDeletesAllocatedHandleOnCompileFailure() throws IOException {
        String source = read("src/main/java/net/oculus/gl/shader/GlShader.java");

        assertTrue(source.contains("} catch (RuntimeException | Error exception) {\n"
            + "            closeFailedShader(exception);\n"
            + "            throw exception;\n"
            + "        }"));
        assertTrue(source.contains("private void closeFailedShader(Throwable failure)"));
        assertTrue(source.contains("destroy();"));
        assertTrue(source.contains("} catch (RuntimeException | Error cleanupFailure) {\n"
            + "            failure.addSuppressed(cleanupFailure);\n"
            + "            LOGGER.debug(\"Failed to delete shader {} after construction failure\", name, cleanupFailure);\n"
            + "        }"));
    }

    @Test
    public void atlasSizeUsesTextureBindingNotifierLikeReferenceCommonUniforms() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("case \"atlasSize\":"));
        assertTrue(source.contains("uniforms.addIntVec2(uniformName, GameplayUniforms::getAtlasSize,\n"
            + "                        StateUpdateNotifiers.bindTextureNotifier);"));
        assertTrue(source.contains("uniforms.addVec2(uniformName, GameplayUniforms::getAtlasSizeFloat,\n"
            + "                        StateUpdateNotifiers.bindTextureNotifier);"));
    }

    @Test
    public void shadowFadeAndShdFadeUseSeparateReferenceFallbacks() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("case \"shadowFade\":\n"
            + "                uniforms.addFloat(uniformName, CompatibilityUniforms::getShadowFade);"));
        assertTrue(source.contains("case \"shdFade\":\n"
            + "                uniforms.addFloat(uniformName, CompatibilityUniforms::getShdFade);"));
        assertFalse(source.contains("case \"shadowFade\":\n"
            + "            case \"shdFade\":\n"
            + "                uniforms.addFloat(uniformName, CompatibilityUniforms::getShadowFade);"));
    }

    @Test
    public void worldAndShadowProgramsUseExternalLevelSamplers() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("usesWorldLevelSamplers() && !hasTextureInput() && isUnavailableAlbedoFallbackSampler(uniformName)"));
        assertTrue(source.contains("samplers.addDynamicSampler(FallbackTextures::getWhiteTexture,\n"
            + "                    \"tex\", \"texture\", \"gtexture\", \"gcolor\", \"colortex0\");"));
        assertTrue(source.contains("usesWorldLevelSamplers() && isWorldAlbedoSampler(uniformName)"));
        assertTrue(source.contains("samplers.addExternalSampler(0, uniformName);"));
        assertTrue(source.contains("usesWorldLevelSamplers() && \"lightmap\".equals(uniformName)"));
        assertTrue(source.contains("if (hasLightmapInput())"));
        assertTrue(source.contains("samplers.addExternalSampler(getLightmapTextureUnit(), uniformName);"));
        assertTrue(source.contains("usesWorldLevelSamplers() && \"iris_overlay\".equals(uniformName)"));
        assertTrue(source.contains("availability != null && availability.overlay"));
        assertTrue(source.contains("samplers.addExternalSampler(getOverlayTextureUnit(), uniformName);"));
        assertTrue(source.contains("samplers.addDynamicSampler(FallbackTextures::getWhiteTexture, uniformName);"));
        assertTrue(source.contains("WORLD_RESERVED_TEXTURE_UNITS = textureUnitSet(0, 1, 2);"));
        assertTrue(source.contains("reservedTextureUnitsForProgram(name, reservedTextureUnits)"));
        assertTrue(source.contains("new HashSet<>(WORLD_RESERVED_TEXTURE_UNITS)"));
        assertTrue(source.contains("if (!shouldAutoBindSampler(uniformName))"));
        assertTrue(source.contains("isLowRenderTargetSampler(uniformName)"));
        assertTrue(source.contains("\"gdepthtex\".equals(uniformName) || \"depthtex2\".equals(uniformName)"));
        assertTrue(source.contains("isShadowLevelProgram() && isWorldDepthSampler(uniformName)"));
        assertTrue(source.contains("return \"depthtex0\".equals(samplerName)"));
        assertFalse(source.contains("return \"gdepthtex\".equals(samplerName)"));
        assertTrue(source.contains("return normalized.startsWith(\"gbuffers_\")"));
        assertTrue(source.contains("|| normalized.equals(\"shadow\")"));
        assertTrue(source.contains("|| normalized.startsWith(\"shadow_\")"));
        assertTrue(source.contains("|| normalized.startsWith(\"shadow.\")"));
        assertFalse(source.contains("normalized.startsWith(\"shadowcomp\")"));
        assertTrue(source.contains("OpenGlHelper.lightmapTexUnit - OpenGlHelper.defaultTexUnit"));
        assertTrue(source.contains("OpenGlHelper.GL_TEXTURE2 - OpenGlHelper.defaultTexUnit"));
    }

    @Test
    public void worldSamplerAutoSkipUsesExactUniformNamesLikeSamplerBindings() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String shouldAutoBindSampler = methodBody(source, "private boolean shouldAutoBindSampler(String uniformName)");
        String lowRenderTarget = methodBody(source, "private static boolean isLowRenderTargetSampler(String samplerName)");
        String worldDepth = methodBody(source, "private static boolean isWorldDepthSampler(String samplerName)");
        String colorRange = methodBody(source,
            "private static boolean isColorTextureIndexInRange(String samplerName, int min, int max)");

        assertFalse(shouldAutoBindSampler.contains("toLowerCase"));
        assertTrue(shouldAutoBindSampler.contains("isLowRenderTargetSampler(uniformName)"));
        assertTrue(shouldAutoBindSampler.contains("\"gdepthtex\".equals(uniformName)"));
        assertTrue(shouldAutoBindSampler.contains("\"depthtex2\".equals(uniformName)"));
        assertTrue(shouldAutoBindSampler.contains("isWorldDepthSampler(uniformName)"));
        assertFalse(shouldAutoBindSampler.contains("normalized"));

        assertTrue(lowRenderTarget.contains("\"gcolor\".equals(samplerName)"));
        assertTrue(lowRenderTarget.contains("isColorTextureIndexInRange(samplerName, 0, 3)"));
        assertTrue(worldDepth.contains("\"depthtex0\".equals(samplerName)"));
        assertTrue(worldDepth.contains("\"depthtex1\".equals(samplerName)"));
        assertTrue(colorRange.contains("samplerName.startsWith(\"colortex\")"));
        assertTrue(colorRange.contains("samplerName.substring(\"colortex\".length())"));
    }

    @Test
    public void unavailableWorldTextureAndLightmapInputsUseWhiteFallbackLikeReference() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String handleUniform = methodBody(source, "private void handleUniform(String uniformName, int glType)");

        int textureFallback = handleUniform.indexOf(
            "usesWorldLevelSamplers() && !hasTextureInput() && isUnavailableAlbedoFallbackSampler(uniformName)");
        int textureFallbackBind = handleUniform.indexOf(
            "samplers.addDynamicSampler(FallbackTextures::getWhiteTexture,\n"
                + "                    \"tex\", \"texture\", \"gtexture\", \"gcolor\", \"colortex0\");",
            textureFallback);
        int externalTexture = handleUniform.indexOf(
            "usesWorldLevelSamplers() && isWorldAlbedoSampler(uniformName)", textureFallbackBind);
        int externalTextureBind = handleUniform.indexOf("samplers.addExternalSampler(0, uniformName);", externalTexture);

        int lightmap = handleUniform.indexOf("usesWorldLevelSamplers() && \"lightmap\".equals(uniformName)");
        int lightmapInputCheck = handleUniform.indexOf("if (hasLightmapInput())", lightmap);
        int externalLightmap = handleUniform.indexOf(
            "samplers.addExternalSampler(getLightmapTextureUnit(), uniformName);", lightmapInputCheck);
        int fallbackLightmap = handleUniform.indexOf(
            "samplers.addDynamicSampler(FallbackTextures::getWhiteTexture, uniformName);", externalLightmap);

        assertTrue(textureFallback >= 0);
        assertTrue(textureFallbackBind > textureFallback);
        assertTrue(externalTexture > textureFallbackBind);
        assertTrue(externalTextureBind > externalTexture);
        assertTrue(lightmap >= 0);
        assertTrue(lightmapInputCheck > lightmap);
        assertTrue(externalLightmap > lightmapInputCheck);
        assertTrue(fallbackLightmap > externalLightmap);

        assertTrue(source.contains("private static boolean isUnavailableAlbedoFallbackSampler(String uniformName)"));
        assertTrue(source.contains("|| \"gcolor\".equals(uniformName)"));
        assertTrue(source.contains("|| \"colortex0\".equals(uniformName)"));
        assertTrue(source.contains("private boolean hasTextureInput()"));
        assertTrue(source.contains("return availability == null || availability.texture;"));
        assertTrue(source.contains("private boolean hasLightmapInput()"));
        assertTrue(source.contains("return availability == null || availability.lightmap;"));
    }

    @Test
    public void entityColorAliasUsesCaptured1_12BrightnessUniform() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("case \"entityColor\":\n"
            + "            case \"iris_entityColor\":\n"
            + "                uniforms.addVec4(uniformName, GameplayUniforms::getEntityColor,\n"
            + "                    GameplayUniforms.getEntityColorNotifier());"));
    }

    @Test
    public void irisNormalMatrixUsesInverseTransposeCapture() throws IOException {
        String source = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");

        assertTrue(source.contains("case \"iris_NormalMatrix\":\n"
            + "                uniforms.addMatrix4(uniformName, state::getNormalMatrix);"));
        assertFalse(source.contains("case \"iris_NormalMatrix\":\n"
            + "                uniforms.addMatrix4(uniformName, state::getModelViewInverse);"));
    }

    @Test
    public void sharedFrameNotifierDrivesNonCameraSmoothedBuiltIns() throws IOException {
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String loader = read("src/main/java/net/oculus/shader/ShaderLoader.java");
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String shadow = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String sodium = read("src/main/java/net/oculus/pipeline/SodiumTerrainPipeline.java");

        assertTrue(builder.contains("private final FrameUpdateNotifier frameUpdateNotifier;"));
        assertTrue(builder.contains("private final PackDirectives packDirectives;"));
        assertTrue(builder.contains("sharedFrameSmoothedOrFallback("));
        assertTrue(builder.contains("new SmoothedFloat(halfLifeUp, halfLifeDown, raw, frameUpdateNotifier);"));
        assertTrue(builder.contains("getWetnessHalfLife(), getDrynessHalfLife(), GameplayUniforms::getRainStrength"));
        assertTrue(builder.contains("15.0F, 15.0F, GameplayUniforms::getRainStrength"));
        assertTrue(builder.contains("SystemTimeUniforms.TIMER::getLastFrameTime"));

        assertTrue(loader.contains("frameUpdateNotifier, programSet.getPackDirectives()"));
        assertTrue(composite.contains("updateNotifier, packDirectives"));
        assertTrue(finalPass.contains("updateNotifier, packDirectives"));
        assertTrue(shadow.contains("frameUpdateNotifier, directives"));
        assertTrue(sodium.contains("frameUpdateNotifier, packDirectives"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void assertUsesExplicitProgramBuilder(String source, String programName) {
        assertTrue(Pattern.compile("ProgramBuilder\\.beginExplicit\\s*\\(\\s*\"" + programName + "\"")
            .matcher(source)
            .find());
    }

    private static void assertDoesNotUseAutomaticProgramBuilder(String source, String programName) {
        assertFalse(Pattern.compile("ProgramBuilder\\.begin\\s*\\(\\s*\"" + programName + "\"")
            .matcher(source)
            .find());
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method " + signature, start >= 0);

        int openBrace = source.indexOf('{', start);
        assertTrue("Missing method body for " + signature, openBrace >= 0);

        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Could not parse method body for " + signature);
    }
}

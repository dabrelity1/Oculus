package net.oculus.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.shader.ShaderPreprocessor;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderPackLoader;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class SodiumTerrainPipelineTest {
    private static final String COMPLEMENTARY_ZIP = "ComplementaryReimagined_r5.6.1.zip";
    private static final String DISABLE_GL_STRING_PROBES_PROPERTY = "oculus.disableGlStringProbes";
    private static String previousGlStringProbeSetting;
    private static String previousGlCapabilityProbeSetting;

    @BeforeClass
    public static void disableGlProbesForHeadlessTerrainSourceTests() {
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

    @Test
    public void terrainSelectionFallsBackThroughOptifineProgramOrder() {
        ProgramSet programSet = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            sourcesFor("gbuffers_textured_lit"),
            ShaderProperties.empty(),
            null);

        SodiumTerrainPipeline pipeline = new SodiumTerrainPipeline(
            "test-pack",
            programSet,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            CustomUniformExpressionManager.empty(),
            null);

        assertTrue(pipeline.isInitialized());
        assertTrue(pipeline.hasTerrainPass());
        assertTrue(pipeline.hasTranslucentPass());
        assertEquals("gbuffers_textured_lit_sodium", pipeline.getTerrainProgramName().get());
        assertEquals("gbuffers_textured_lit_sodium", pipeline.getTranslucentProgramName().get());
        assertTrue(pipeline.getTerrainVertexShaderSource().get().contains("attribute vec3 iris_Pos;"));
    }

    @Test
    public void waterAndShadowSourcesProduceDistinctPreparedPasses() {
        ProgramSet programSet = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            sourcesFor("gbuffers_terrain", "gbuffers_water", "shadow"),
            ShaderProperties.empty(),
            null);

        SodiumTerrainPipeline pipeline = new SodiumTerrainPipeline(
            "test-pack",
            programSet,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            CustomUniformExpressionManager.empty(),
            null);

        assertTrue(pipeline.hasTerrainPass());
        assertTrue(pipeline.hasTranslucentPass());
        assertTrue(pipeline.hasShadowPass());
        assertEquals("gbuffers_terrain_sodium", pipeline.getTerrainProgramName().get());
        assertEquals("gbuffers_water_sodium", pipeline.getTranslucentProgramName().get());
        assertEquals("shadow_sodium", pipeline.getShadowProgramName().get());
    }

    @Test
    public void sodiumTerrainPreparationDoesNotRunWorldAttributeTransformer() {
        ProgramSet programSet = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            sourcesFor("gbuffers_terrain"),
            ShaderProperties.empty(),
            null);

        SodiumTerrainPipeline pipeline = new SodiumTerrainPipeline(
            "test-pack",
            programSet,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            CustomUniformExpressionManager.empty(),
            null);

        String terrainVertex = pipeline.getTerrainVertexShaderSource().get();

        assertTrue(terrainVertex.contains("attribute vec3 iris_Pos;"));
        assertFalse("Sodium terrain patching must not run AttributeTransformer texture-matrix injection",
            terrainVertex.contains("iris_TextureMatrix[8]"));
        assertFalse("Sodium terrain patching must not inject AttributeTransformer lightmap constants",
            terrainVertex.contains("iris_ONE_OVER_256"));
    }

    @Test
    public void complementaryZipProducesPreparedSodiumTerrainSources() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        Assume.assumeTrue("Complementary Reimagined zip is not available",
            Files.isRegularFile(shaderpacks.resolve(COMPLEMENTARY_ZIP)));

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(
            shaderpacks,
            COMPLEMENTARY_ZIP,
            complementaryOverrides());
        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());

        SodiumTerrainPipeline pipeline = new SodiumTerrainPipeline(
            pack.getName(),
            overworld,
            ShaderPreprocessor.createEnvironmentDefines(overworld),
            null,
            null,
            null,
            null,
            null,
            CustomUniformExpressionManager.empty(),
            null);

        assertTrue(pipeline.isInitialized());
        assertTrue(pipeline.hasTerrainPass());
        assertTrue(pipeline.hasTranslucentPass());
        assertFalse(pipeline.hasShadowPass());
        assertEquals("gbuffers_terrain_sodium", pipeline.getTerrainProgramName().get());
        assertEquals("gbuffers_water_sodium", pipeline.getTranslucentProgramName().get());

        String terrainVertex = pipeline.getTerrainVertexShaderSource().get();
        assertTrue(terrainVertex.contains("#define OVERWORLD"));
        assertTrue(terrainVertex.contains("#define VERTEX_SHADER"));
        assertTrue(terrainVertex.contains("attribute vec3 iris_Pos;"));
        assertTrue(terrainVertex.contains("uniform vec3 u_ModelScale;"));
        assertTrue(terrainVertex.contains("vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0)"));

        String translucentVertex = pipeline.getTranslucentVertexShaderSource().get();
        assertTrue(translucentVertex.contains("#define PROGRAM_GBUFFERS_WATER_SODIUM"));
        assertTrue(translucentVertex.contains("attribute vec2 iris_LightCoord;"));

        String terrainFragment = pipeline.getTerrainFragmentShaderSource().get();
        assertTrue(terrainFragment.contains("#define FRAGMENT_SHADER"));
        assertTrue(terrainFragment.contains("uniform mat4 iris_ModelViewMatrix;"));
    }

    @Test
    public void complementaryRuntimeTerrainSourcesDeclareRelictiumSymbolsBeforeUse() throws Exception {
        Path shaderpacks = Paths.get("run", "shaderpacks");
        Assume.assumeTrue("Complementary Reimagined zip is not available",
            Files.isRegularFile(shaderpacks.resolve(COMPLEMENTARY_ZIP)));

        ShaderPack pack = ShaderPackLoader.loadFromShaderpacksDirectory(
            shaderpacks,
            COMPLEMENTARY_ZIP,
            complementaryRuntimeOverrides());
        ProgramSet overworld = pack.getProgramSet(NamespacedId.overworld());

        SodiumTerrainPipeline pipeline = new SodiumTerrainPipeline(
            pack.getName(),
            overworld,
            ShaderPreprocessor.createEnvironmentDefines(overworld),
            null,
            null,
            null,
            null,
            null,
            CustomUniformExpressionManager.empty(),
            null);

        assertTrue(pipeline.hasTerrainPass());
        assertTrue(pipeline.hasTranslucentPass());
        assertTrue(pipeline.hasShadowPass());

        String terrainVertex = pipeline.getTerrainVertexShaderSource().get();
        String translucentVertex = pipeline.getTranslucentVertexShaderSource().get();
        String shadowVertex = pipeline.getShadowVertexShaderSource().get();

        assertRelictiumVertexSymbolsReady(terrainVertex);
        assertRelictiumVertexSymbolsReady(translucentVertex);
        assertRelictiumVertexSymbolsReady(shadowVertex);
    }

    @Test
    public void wrappedRelictiumProgramsKeepRuntimeBindingSurface() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/SodiumTerrainPipeline.java");
        String body = methodBody(source, "public Program buildProgramBindings(String programName, int programId, boolean shadowProgram)");

        int prepareResources = body.indexOf("prepareResources.run();");
        int textureInitialize = body.indexOf("customTextureManager.initialize();", prepareResources);
        int globalOverrides = body.indexOf("customTextureManager.applyGlobalOverrides();", textureInitialize);
        int levelInputs = source.indexOf(
            "private static final InputAvailability RELICTIUM_LEVEL_INPUTS = new InputAvailability(true, true, false);");
        int wrap = body.indexOf(
            "ProgramBuilder.wrapLinkedProgram(programName, programId, customUniforms,\n"
                + "            RELICTIUM_LEVEL_INPUTS,\n"
                + "            frameUpdateNotifier, packDirectives);",
            globalOverrides);
        int requireShadowTargets = body.indexOf(
            "ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);",
            wrap);
        int shadowSamplers = body.indexOf("shadowMap.applySamplerBindings(builder);", requireShadowTargets);
        int renderTargets = body.indexOf(
            "RenderTargets renderTargets = renderTargetsSupplier == null ? null : renderTargetsSupplier.get();",
            shadowSamplers);
        int renderTargetSamplers = body.indexOf(
            "IrisSamplers.addRenderTargetSamplerBindings(builder, flippedBuffersSupplier, renderTargets, false);",
            renderTargets);
        int worldDepthGuard = body.indexOf("if (!shadowProgram)", renderTargetSamplers);
        int worldDepthSamplers = body.indexOf("IrisSamplers.addWorldDepthSamplerBindings(builder, renderTargets);",
            worldDepthGuard);
        int renderTargetImages = body.indexOf(
            "IrisImages.addRenderTargetImages(builder, flippedBuffersSupplier, renderTargets);",
            worldDepthSamplers);
        int shadowImages = body.indexOf("IrisImages.addShadowColorImages(builder, shadowMap);", renderTargetImages);
        int customTextures = body.indexOf("customTextureManager.applyCustomSamplers(programName, builder.samplers());",
            shadowImages);
        int customImages = body.indexOf("customImageManager.applyToProgram(builder);", customTextures);
        int build = body.indexOf("return builder.build();", customImages);

        assertTrue("Relictium wrapper must prepare pipeline resources before wrapping an active GL program",
            prepareResources >= 0);
        assertTrue("Custom textures must be initialized before wrapped sampler discovery runs",
            textureInitialize > prepareResources && globalOverrides > textureInitialize);
        assertTrue("Wrapped Relictium programs must use the 1.16.5 terrain level-input availability",
            levelInputs >= 0 && wrap > globalOverrides);
        assertTrue("Wrapped Relictium programs must use the frame notifier and pack directives",
            wrap > globalOverrides);
        assertFalse("The no-notifier wrapped-program overload would lose source-backed smoothing/directives",
            body.contains("ProgramBuilder.wrapLinkedProgram(programName, programId, customUniforms);"));
        assertTrue("Wrapped Relictium programs must fail clearly when declared shadow targets are unavailable",
            requireShadowTargets > wrap);
        assertFalse("Wrapped Relictium programs must not silently skip a required missing shadow map",
            body.contains("ShadowMap shadowMap = shadowMapSupplier == null ? null : shadowMapSupplier.get();"));
        assertTrue("Shadow sampler bindings must still be offered to wrapped terrain programs",
            shadowSamplers > requireShadowTargets);
        assertTrue("Wrapped terrain programs must explicitly bind render-target samplers like 1.16.5 terrain",
            renderTargets > shadowSamplers && renderTargetSamplers > renderTargets);
        assertFalse("Wrapped terrain programs must let sampler/image helpers fail clearly when render targets are missing",
            body.contains("if (renderTargets != null)"));
        assertTrue("World depth samplers must be present only on non-shadow terrain programs",
            worldDepthGuard > renderTargetSamplers && worldDepthSamplers > worldDepthGuard);
        assertTrue("Render-target images must be offered to wrapped terrain programs",
            renderTargetImages > worldDepthSamplers);
        assertTrue("Shadow color images must be offered after render-target image binding",
            shadowImages > renderTargetImages);
        assertTrue("Custom texture and custom image bindings must be applied before build()",
            customTextures > shadowImages && customImages > customTextures && build > customImages);
    }

    private static Function<AbsolutePackPath, String> sourcesFor(String... programs) {
        return path -> {
            String key = path.getPathString();
            for (String program : programs) {
                if (key.equals("/" + program + ".vsh")) {
                    return "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
                }
                if (key.equals("/" + program + ".fsh")) {
                    return "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";
                }
            }
            return null;
        };
    }

    private static Map<String, String> complementaryOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("COLORED_LIGHTING", "128");
        overrides.put("WORLD_SPACE_REFLECTIONS", "1");
        overrides.put("SHADOW_QUALITY", "-1");
        return overrides;
    }

    private static Map<String, String> complementaryRuntimeOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("COLORED_LIGHTING", "128");
        overrides.put("WORLD_SPACE_REFLECTIONS", "1");
        return overrides;
    }

    private static void assertRelictiumVertexSymbolsReady(String source) {
        assertContains(source, "uniform mat4 iris_LightmapTextureMatrix;");
        assertContains(source, "uniform mat4 iris_NormalMatrix;");
        assertContains(source, "uniform mat4 iris_ModelViewMatrix;");
        assertContains(source, "uniform mat4 u_ModelViewProjectionMatrix;");
        assertContains(source, "attribute vec3 iris_Pos;");
        assertContains(source, "attribute vec3 iris_Normal;");
        assertContains(source, "uniform vec3 u_ModelScale;");
        assertContains(source, "attribute vec4 iris_ModelOffset;");
        assertNotInsideBlockComment(source, "uniform mat4 iris_LightmapTextureMatrix;");
        assertNotInsideBlockComment(source, "attribute vec3 iris_Pos;");

        assertDeclarationBeforeUse(source, "uniform mat4 iris_LightmapTextureMatrix;",
            "iris_LightmapTextureMatrix *");
        assertDeclarationBeforeUseIfPresent(source, "uniform mat4 iris_NormalMatrix;", "mat3(iris_NormalMatrix)");
        assertDeclarationBeforeUseIfPresent(source, "uniform mat4 u_ModelViewProjectionMatrix;",
            "u_ModelViewProjectionMatrix *");
        assertDeclarationBeforeUseIfPresent(source, "attribute vec3 iris_Pos;", "iris_Pos * u_ModelScale");
        assertDeclarationBeforeUseIfPresent(source, "uniform vec3 u_ModelScale;", "iris_Pos * u_ModelScale");
        assertDeclarationBeforeUseIfPresent(source, "attribute vec4 iris_ModelOffset;", "iris_ModelOffset.xyz");
    }

    private static void assertContains(String source, String needle) {
        assertTrue("Missing source text: " + needle, source.contains(needle));
    }

    private static void assertDeclarationBeforeUse(String source, String declaration, String use) {
        int declarationIndex = source.indexOf(declaration);
        int useIndex = source.indexOf(use);
        assertTrue("Missing declaration: " + declaration, declarationIndex >= 0);
        assertTrue("Missing use: " + use, useIndex >= 0);
        assertTrue("Declaration must appear before use: " + declaration,
            declarationIndex < useIndex);
    }

    private static void assertDeclarationBeforeUseIfPresent(String source, String declaration, String use) {
        int useIndex = source.indexOf(use);
        if (useIndex < 0) {
            return;
        }

        int declarationIndex = source.indexOf(declaration);
        assertTrue("Missing declaration: " + declaration, declarationIndex >= 0);
        assertTrue("Declaration must appear before use: " + declaration,
            declarationIndex < useIndex);
    }

    private static void assertNotInsideBlockComment(String source, String needle) {
        int index = source.indexOf(needle);
        assertTrue("Missing source text: " + needle, index >= 0);
        int commentStart = source.lastIndexOf("/*", index);
        int commentEnd = source.lastIndexOf("*/", index);
        assertTrue("Source text is hidden inside a block comment: " + needle,
            commentStart < 0 || commentEnd > commentStart);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method signature " + signature, start >= 0);

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

        throw new AssertionError("Unterminated method body for " + signature);
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}

package net.oculus.shader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ShaderLoaderSourceTest {
    @Test
    public void worldProgramsCompileInputAvailabilityVariantsLikeReferenceProgramTable() throws Exception {
        String source = read("src/main/java/net/oculus/shader/ShaderLoader.java");

        assertTrue(source.contains("new HashMap<>()"));
        assertTrue(source.contains("if (usesAvailabilityVariants(source.getName())) {\n" +
            "                    successfulForSource = compileAvailabilityVariants(source, failedPrograms);\n" +
            "                } else {"));
        assertTrue(source.contains("for (int packed = 0; packed < InputAvailability.NUM_VALUES; packed++)"));
        assertTrue(source.contains("InputAvailability.unpack(packed)"));
        assertTrue(source.contains("availabilityPrograms.put(key, compileProgram(source, availability))"));
        assertTrue(source.contains("IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS, availability"));
        assertFalse(source.contains("Collections.emptySet(), availability"));
        assertFalse(source.contains("Program program = compileProgram(source);\n" +
            "                programs.put(source.getName(), program);"));
    }

    @Test
    public void shaderLoaderReportsVariantProgramObjectsWithoutRequiringUnspecializedMapEntries() throws Exception {
        String loader = read("src/main/java/net/oculus/shader/ShaderLoader.java");
        String pipeline = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");

        assertTrue(loader.contains("public int getProgramCount()"));
        assertTrue(loader.contains("return programs.size() + availabilityPrograms.size();"));
        assertTrue(loader.contains("public boolean hasCompiledPrograms()"));
        assertTrue(pipeline.contains("shaderLoader.getProgramCount()"));
        assertFalse(pipeline.contains("!shaderLoader.hasCompiledPrograms()"));
        assertFalse(pipeline.contains("shaderLoader.getPrograms().isEmpty()"));
    }

    @Test
    public void shaderLoaderEmitsCompileProgressAndTimingDiagnostics() throws Exception {
        String source = read("src/main/java/net/oculus/shader/ShaderLoader.java");

        assertTrue(source.contains("Compiling {} shader source program(s) for pack {}"));
        assertTrue(source.contains("Shader compile progress for pack {}: {}/{} source programs"));
        assertTrue(source.contains("Slow shader compile for pack {} program {} [{}]"));
        assertTrue(source.contains("Shader compile timing for pack {} program {} [{}]"));
    }

    @Test
    public void availabilityVariantsAreLimitedToWorldAndShadowPrograms() throws Exception {
        String source = read("src/main/java/net/oculus/shader/ShaderLoader.java");

        assertTrue(source.contains("private static boolean usesAvailabilityVariants(String programName)"));
        assertTrue(source.contains("normalized.startsWith(\"gbuffers_\")"));
        assertTrue(source.contains("normalized.equals(\"shadow\")"));
        assertTrue(source.contains("normalized.startsWith(\"shadow_\")"));
        assertTrue(source.contains("normalized.startsWith(\"shadow.\")"));
    }

    @Test
    public void worldAndShadowVariantLookupDoesNotFallbackToUnspecializedProgram() throws Exception {
        String source = read("src/main/java/net/oculus/shader/ShaderLoader.java");

        assertTrue(source.contains("Program program = availabilityPrograms.get(new ProgramVariantKey(name, availability));"));
        assertTrue(source.contains("if (usesAvailabilityVariants(name)) {\n            return program;\n        }"));
        assertTrue(source.contains("return program == null ? getProgram(name) : program;"));
    }

    @Test
    public void worldAndRootShadowProgramResourceBindingsStayInReferenceOrder() throws Exception {
        String source = read("src/main/java/net/oculus/shader/ShaderLoader.java");
        String compile = methodBody(source, "private Program compileProgram(ProgramSource source, InputAvailability availability)");
        String shadowSamplersBody = methodBody(source,
            "private void applyShadowSamplerBindings(ProgramBuilder builder, String programName)");
        String images = methodBody(source, "private void applyImageBindings(ProgramBuilder builder, String programName)");

        int begin = compile.indexOf("ProgramBuilder builder = ProgramBuilder.begin(");
        int shadowSamplers = compile.indexOf("applyShadowSamplerBindings(builder, source.getName());", begin);
        int customTextures = compile.indexOf("customTextureManager.applyCustomSamplers(source.getName(), builder.samplers());",
            shadowSamplers);
        int imageBindings = compile.indexOf("applyImageBindings(builder, source.getName());", customTextures);
        int customImages = compile.indexOf("customImageManager.applyToProgram(builder);", imageBindings);
        int build = compile.indexOf("Program program = builder.build();", customImages);

        assertTrue("Generic world/root-shadow programs must still use availability-aware ProgramBuilder setup",
            begin >= 0 && compile.contains(
                "IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS, availability, frameUpdateNotifier, programSet.getPackDirectives()"));
        assertTrue("Shadow samplers should be applied before custom texture overrides like the 1.16.5 pass setup",
            shadowSamplers > begin && customTextures > shadowSamplers);
        assertTrue("Render-target and shadow image bindings must be installed before custom images and build()",
            imageBindings > customTextures && customImages > imageBindings && build > customImages);
        assertFalse("Generic gbuffer/root-shadow programs should not duplicate the explicit fullscreen/compute sampler path",
            compile.contains("IrisSamplers.addRenderTargetSamplerBindings("));

        assertTrue(shadowSamplersBody.contains(
            "ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);"));
        assertTrue(shadowSamplersBody.contains("shadowMap.applySamplerBindings(builder, programName);"));
        assertTrue(images.contains("RenderTargets renderTargets = renderTargetsSupplier == null ? null : renderTargetsSupplier.get();"));
        assertTrue(images.contains("IrisImages.addRenderTargetImages(builder, flippedBuffersSupplier, renderTargets);"));
        assertTrue(images.contains("ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);"));
        assertTrue(images.contains("IrisImages.addShadowColorImages(builder, shadowMap);"));

        int renderTargets = images.indexOf(
            "RenderTargets renderTargets = renderTargetsSupplier == null ? null : renderTargetsSupplier.get();");
        int renderTargetImages = images.indexOf(
            "IrisImages.addRenderTargetImages(builder, flippedBuffersSupplier, renderTargets);",
            renderTargets);
        int shadowTargets = images.indexOf(
            "ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);",
            renderTargetImages);
        assertTrue("Render-target images must still go through IrisImages when targets are unavailable, "
                + "so active colorimg uniforms fail clearly",
            renderTargetImages > renderTargets && shadowTargets > renderTargetImages);
        assertFalse("ShaderLoader must not silently skip active render-target image requirements when targets are missing",
            images.contains("if (renderTargets != null)"));
    }

    @Test
    public void shadowCompositeSourcesAreDiscoveredButNotCompiledByGenericShaderLoader() throws Exception {
        String source = read("src/main/java/net/oculus/shader/ShaderLoader.java");

        assertFalse(source.contains("addArray(programSet.getShadowComposite(), sources);"));
    }

    @Test
    public void postprocessSourcesAreCompiledByPostprocessRenderersNotGenericShaderLoader() throws Exception {
        String loader = read("src/main/java/net/oculus/shader/ShaderLoader.java");
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertFalse(loader.contains("addArray(programSet.getPrepare(), sources);"));
        assertFalse(loader.contains("addArray(programSet.getDeferred(), sources);"));
        assertFalse(loader.contains("addArray(programSet.getComposite(), sources);"));
        assertFalse(loader.contains("addOptional(programSet.getCompositeFinal(), sources);"));
        assertFalse(loader.contains("private void addArray("));
        assertTrue(composite.contains("ProgramBuilder.begin("));
        assertTrue(finalPass.contains("ProgramBuilder.begin("));
    }

    @Test
    public void sodiumTerrainPreparationSkipsInputAvailabilityBranch() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/SodiumTerrainPipeline.java");

        assertTrue(source.contains("ShaderSourcePreparer.prepareProgram("));
        assertTrue(source.contains(
            "private static final InputAvailability RELICTIUM_LEVEL_INPUTS = new InputAvailability(true, true, false);"));
        assertTrue(source.contains(
            "ProgramBuilder.wrapLinkedProgram(programName, programId, customUniforms,\n"
                + "            RELICTIUM_LEVEL_INPUTS,\n"
                + "            frameUpdateNotifier, packDirectives);"));
        assertFalse(source.contains("ShaderSourcePreparer.prepareProgram(\n"
            + "            packName,\n"
            + "            programName,\n"
            + "            vertex,\n"
            + "            geometry,\n"
            + "            fragment,\n"
            + "            environmentDefines,\n"
            + "            RELICTIUM_LEVEL_INPUTS);"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
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

        throw new AssertionError("Unable to read method body for " + signature);
    }
}

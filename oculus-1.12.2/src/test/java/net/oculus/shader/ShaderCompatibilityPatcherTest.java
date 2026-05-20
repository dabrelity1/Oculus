package net.oculus.shader;

import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.InputAvailability;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ShaderCompatibilityPatcherTest {
    private static final String CORE_PROFILE_VERTEX_SHADER_ERROR =
        "Vertex shaders must be in the compatibility profile to run properly!";
    private static final String INTERNAL_INTERFACE_ERROR_PREFIX =
        "Detected a potential reference to unstable and internal Iris shader interfaces (iris_ and irisMain). "
            + "This isn't currently supported. Violation: ";
    private static final String INTERNAL_INTERFACE_ERROR_SUFFIX = ". See debugging.md for more information.";

    @Test
    public void rewritesLegacyFogBlock() {
        String legacySource = "#version 120\n" +
            "#if defined SET_FOG_COLOR\n" +
            "        block_color = clamp(block_color, vec3(0.0), vec3(50.0));\n" +
            "        gl_FragData[0] = vec4(block_color, 1.0);\n" +
            "        gl_FragData[1] = vec4(block_color, 1.0);\n" +
            "#endif";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "prepare", ShaderType.FRAGMENT, legacySource);

        assertTrue("Expected fog_color helper to be injected", patched.contains("vec3 fog_color"));
        assertTrue(patched.contains("gl_FragData[0].rgb = fog_color;"));
        assertFalse("Legacy vec4 constructor should be removed", patched.contains("vec4(block_color, 1.0)"));
    }

    @Test
    public void leavesModernFogBlockUntouched() {
        String modernSource = "#if defined SET_FOG_COLOR\n" +
            "    vec3 fog_color = clamp(block_color.rgb, vec3(0.0), vec3(50.0));\n" +
            "    gl_FragData[0].rgb = fog_color;\n" +
            "    gl_FragData[0].a = 1.0;\n" +
            "    gl_FragData[1].rgb = fog_color;\n" +
            "    gl_FragData[1].a = 1.0;\n" +
            "#endif";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "prepare", ShaderType.FRAGMENT, modernSource);
        assertEquals(modernSource, patched);
    }

    @Test
    public void rewritesLightmapTextureMatrixIndexOne() {
        String source = "#version 120\n" +
            "#extension GL_EXT_gpu_shader4 : enable\n" +
            "#define VERTEX_SHADER\n" +
            "void main() {\n" +
            "    vec2 lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;\n" +
            "    vec2 texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.VERTEX, source);

        assertTrue(patched.contains("uniform mat4 iris_LightmapTextureMatrix;\n"));
        assertTrue(patched.contains("(iris_LightmapTextureMatrix * gl_MultiTexCoord1).xy"));
        assertTrue("Texture matrix 0 must keep using vanilla texture coordinates",
            patched.contains("(gl_TextureMatrix[0] * gl_MultiTexCoord0).xy"));
        assertFalse(patched.contains("gl_TextureMatrix[1]"));
    }

    @Test
    public void internalIrisInterfaceReferenceThrowsLikeTransformPatcher() {
        String source = "#version 120\n" +
            "uniform mat4 iris_LightmapTextureMatrix;\n" +
            "void main() {\n" +
            "    vec2 lmCoord = (gl_TextureMatrix [ 1 ] * gl_MultiTexCoord1).xy;\n" +
            "}\n";

        assertInternalInterfaceError(source, "iris_LightmapTextureMatrix");
    }

    @Test
    public void internalIrisInterfaceGuardSkipsCommentsStringsPreprocessorAndGeneratedSodiumSources() {
        String source = "#version 120\n" +
            "#define KEEP iris_Pos\n" +
            "// iris_LightmapTextureMatrix\n" +
            "/* irisMain */\n" +
            "void main() {\n" +
            "    const char* marker = \"iris_Pos irisMain\";\n" +
            "    gl_Position = gl_Vertex;\n" +
            "}\n";

        assertEquals(source, ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.VERTEX, source));

        String generatedSodium = "#version 130\n" +
            "attribute vec3 iris_Pos;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(iris_Pos, 1.0);\n" +
            "}\n";

        assertEquals(generatedSodium, ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_terrain_sodium",
            ShaderType.VERTEX,
            generatedSodium));
    }

    @Test
    public void raisesLegacyRasterShaderStorageBufferSourcesToCompatibilityProfile() {
        String source = "#version 130\n" +
            "#extension GL_ARB_shader_storage_buffer_object : enable\n" +
            "layout(std430, binding = 0) readonly buffer blockDataSSBO {\n" +
            "    vec4 data[];\n" +
            "};\n" +
            "void main() { gl_FragData[0] = data[0]; }\n";

        String patched = ShaderCompatibilityPatcher.patch("Complementary", "composite", ShaderType.FRAGMENT, source);

        assertTrue(patched.startsWith("#version 430 compatibility\n"));
        assertTrue(patched.contains("layout(std430, binding = 0) readonly buffer blockDataSSBO"));
        assertTrue(patched.contains("#extension GL_ARB_shader_storage_buffer_object : enable"));
    }

    @Test
    public void leavesComputeAndCommentOnlyShaderStorageBufferTextAtOriginalVersion() {
        String compute = "#version 430\n" +
            "layout(std430, binding = 0) buffer blockDataSSBO { uint data[]; };\n" +
            "void main() {}\n";
        assertEquals(compute, ShaderCompatibilityPatcher.patch("Complementary", "shadowcomp", ShaderType.COMPUTE, compute));

        String commentOnly = "#version 130\n" +
            "// layout(std430, binding = 0) buffer blockDataSSBO\n" +
            "void main() { gl_FragData[0] = vec4(1.0); }\n";
        assertEquals(commentOnly, ShaderCompatibilityPatcher.patch(
            "Complementary", "composite", ShaderType.FRAGMENT, commentOnly));
    }

    @Test
    public void ignoresLightmapTextureMatrixInsideCommentsAndStrings() {
        String source = "#version 120\n" +
            "// gl_TextureMatrix[1]\n" +
            "/* gl_TextureMatrix[1] */\n" +
            "const char marker = 'g';\n" +
            "void main() {\n" +
            "    vec2 texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.VERTEX, source);

        assertEquals(source, patched);
    }

    @Test
    public void availabilityPatchAliasesModernLightmapTexCoordTo1Dot12UnitWhenLightmapIsPresent() {
        String source = "#version 120\n" +
            "// gl_MultiTexCoord2 remains in comments\n" +
            "void main() {\n" +
            "    vec4 lm = gl_MultiTexCoord2;\n" +
            "    vec4 legacyLm = gl_MultiTexCoord1;\n" +
            "    vec4 tex = gl_MultiTexCoord0;\n" +
            "    gl_Position = lm + legacyLm + tex;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_textured_lit",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, true, false));

        assertTrue(patched.contains("// gl_MultiTexCoord2 remains in comments"));
        assertTrue(patched.contains("vec4 lm = gl_MultiTexCoord1;"));
        assertTrue(patched.contains("vec4 legacyLm = gl_MultiTexCoord1;"));
        assertFalse(patched.contains("vec4 lm = gl_MultiTexCoord2;"));
        assertTrue(patched.contains("vec4 tex = gl_MultiTexCoord0;"));
    }

    @Test
    public void availabilityPatchSuppliesDummyTexCoordsForMissingTextureInputs() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    vec4 combined = gl_MultiTexCoord0 + gl_MultiTexCoord1 + gl_MultiTexCoord2;\n" +
            "    gl_Position = combined;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_basic",
            ShaderType.VERTEX,
            source,
            new InputAvailability(false, false, false));

        assertFalse(patched.contains("gl_MultiTexCoord0"));
        assertFalse(patched.contains("gl_MultiTexCoord1"));
        assertFalse(patched.contains("gl_MultiTexCoord2"));
        assertTrue(patched.contains("vec4 combined = vec4(240.0, 240.0, 0.0, 1.0)"));
    }

    @Test
    public void legacyTexCoordAvailabilityPatchRequiresAvailabilityContext() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    vec4 lm = gl_MultiTexCoord1;\n" +
            "    gl_Position = lm;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.VERTEX, source);

        assertEquals(source, patched);
    }

    @Test
    public void availabilityPatchReplacesFullTextureMatrixArrayWith1Dot12LightmapIndex() {
        String source = "#version 120\n" +
            "// gl_TextureMatrix[1] remains in comments\n" +
            "void main() {\n" +
            "    vec4 base = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n" +
            "    vec4 lm = gl_TextureMatrix[1] * gl_MultiTexCoord1;\n" +
            "    vec4 spare = gl_TextureMatrix[2] * gl_MultiTexCoord0;\n" +
            "    gl_Position = base + lm + spare;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_textured_lit",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, true, false));

        assertTrue(patched.contains("// gl_TextureMatrix[1] remains in comments"));
        assertTrue(patched.contains("const float iris_ONE_OVER_256 = 0.00390625;"));
        assertTrue(patched.contains("const float iris_ONE_OVER_32 = iris_ONE_OVER_256 * 8;"));
        assertTrue(patched.contains("mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[1];"));
        assertTrue(patched.contains("mat4 iris_TextureMatrix[8] = mat4[8](gl_TextureMatrix[0], iris_LightmapTextureMatrix"));
        assertTrue(patched.contains("vec4 base = iris_TextureMatrix[0] * gl_MultiTexCoord0;"));
        assertTrue(patched.contains("vec4 lm = iris_TextureMatrix[1] * gl_MultiTexCoord1;"));
        assertTrue(patched.contains("vec4 spare = iris_TextureMatrix[2] * gl_MultiTexCoord0;"));
        assertFalse(patched.contains("uniform mat4 iris_LightmapTextureMatrix;"));
    }

    @Test
    public void availabilityPatchInjectsTextureMatrixArrayEvenWithoutOriginalReferences() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    gl_Position = gl_Vertex;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_basic",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, true, false));

        assertTrue(patched.contains("const float iris_ONE_OVER_256 = 0.00390625;"));
        assertTrue(patched.contains("mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[1];"));
        assertTrue(patched.contains("mat4 iris_TextureMatrix[8] = mat4[8](gl_TextureMatrix[0], iris_LightmapTextureMatrix"));
        assertTrue(patched.contains("gl_Position = gl_Vertex;"));
    }

    @Test
    public void availabilityPatchUsesDummyLightmapTextureMatrixWhenLightmapInputIsMissing() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    vec4 lm = gl_TextureMatrix[1] * gl_MultiTexCoord1;\n" +
            "    gl_Position = lm;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_basic",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, false, false));

        assertTrue(patched.contains("mat4 iris_LightmapTextureMatrix = mat4(iris_ONE_OVER_256"));
        assertTrue(patched.contains("vec4 lm = iris_TextureMatrix[1] * vec4(240.0, 240.0, 0.0, 1.0);"));
        assertFalse(patched.contains("uniform mat4 iris_LightmapTextureMatrix;"));
    }

    @Test
    public void coreProfileVertexAvailabilityThrowsLikeAttributeTransformer() {
        String source = "#version 150 core\n" +
            "void main() {\n" +
            "    gl_Position = gl_MultiTexCoord2;\n" +
            "}\n";

        assertCoreProfileVertexAttributeError(source);
    }

    @Test
    public void implicitCoreVertexAvailabilityThrowsWhenVersionAbove140() {
        String source = "#version 150\n" +
            "void main() {\n" +
            "    gl_Position = gl_MultiTexCoord2;\n" +
            "}\n";

        assertCoreProfileVertexAttributeError(source);
    }

    @Test
    public void version140WithoutProfileStillRunsAttributeTransformerLikeCompatibilityProfile() {
        String source = "#version 140\n" +
            "void main() {\n" +
            "    gl_Position = gl_MultiTexCoord2;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_textured_lit",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, true, false));

        assertTrue(patched.contains("gl_Position = gl_MultiTexCoord1;"));
    }

    @Test
    public void explicitCompatibilityProfileVertexAvailabilityStillAppliesLegacyAttributeTransforms() {
        String source = "#version 150 compatibility\n" +
            "void main() {\n" +
            "    vec4 lm = gl_TextureMatrix[1] * gl_MultiTexCoord2;\n" +
            "    vec4 mid = gl_MultiTexCoord3;\n" +
            "    gl_Position = lm + mid;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_textured_lit",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, true, false));

        assertTrue(patched.contains("mat4 iris_TextureMatrix[8] = mat4[8]("));
        assertTrue(patched.contains("vec4 lm = iris_TextureMatrix[1] * gl_MultiTexCoord1;"));
        assertTrue(patched.contains("attribute vec4 mc_midTexCoord;\n"));
        assertTrue(patched.contains("vec4 mid = mc_midTexCoord;"));
        assertFalse(patched.contains("gl_MultiTexCoord2"));
        assertFalse(patched.contains("gl_MultiTexCoord3"));
    }

    @Test
    public void coreProfileFragmentAvailabilitySkipsAttributeTransforms() {
        String source = "#version 150 core\n" +
            "void main() {\n" +
            "    vec4 lm = gl_TextureMatrix[1] * gl_MultiTexCoord2;\n" +
            "    gl_FragColor = lm;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_textured_lit",
            ShaderType.FRAGMENT,
            source,
            new InputAvailability(false, false, false));

        assertTrue(patched.contains("gl_TextureMatrix[1] * gl_MultiTexCoord2"));
        assertFalse(patched.contains("iris_TextureMatrix"));
        assertFalse(patched.contains("vec4(240.0, 240.0, 0.0, 1.0)"));
    }

    @Test
    public void coreProfileVertexWithoutAvailabilityDoesNotInjectLegacyAttributeAlias() {
        String source = "#version 150\n" +
            "void main() {\n" +
            "    vec4 mid = gl_MultiTexCoord3;\n" +
            "    gl_Position = vec4(mid.xy, 0.0, 1.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite", ShaderType.VERTEX, source);

        assertEquals(source, patched);
    }

    @Test
    public void overlayAvailabilityPromotesFragmentEntityColorUniformToVarying() {
        String source = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = entityColor;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_entities",
            ShaderType.FRAGMENT,
            source,
            new InputAvailability(true, true, true));

        assertFalse(patched.contains("uniform vec4 entityColor;"));
        assertTrue(patched.contains("varying vec4 entityColor;"));
        assertTrue(patched.contains("gl_FragColor = entityColor;"));
        assertFalse(patched.contains("entityColorGS"));
        assertTrue(patched.contains("mat4 iris_TextureMatrix[8] = mat4[8]("));
    }

    @Test
    public void overlayAvailabilityPromotesVertexEntityColorThroughCaptured1_12Uniform() {
        String source = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_Position = gl_Vertex;\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_entities",
            ShaderType.VERTEX,
            source,
            new InputAvailability(true, true, true));

        assertFalse(patched.contains("uniform vec4 entityColor;"));
        assertTrue(patched.contains("uniform vec4 iris_entityColor;"));
        assertTrue(patched.contains("varying vec4 entityColor;"));
        assertTrue(patched.contains("entityColor = iris_entityColor;"));
        assertTrue(patched.contains("entityColor.rgb *= float(entityColor.a != 0.0);"));
        assertFalse(patched.contains("texture2D(iris_overlay"));
        assertFalse(patched.contains("1.0 - overlayColor.a"));
        assertTrue(patched.contains("mat4 iris_TextureMatrix[8] = mat4[8]("));
    }

    @Test
    public void overlayAvailabilityRenamesFragmentPassthroughWhenGeometryIsPresent() {
        String vertex = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_Position = gl_Vertex;\n" +
            "}\n";
        String geometry = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    vec4 color = entityColor;\n" +
            "    gl_Position = gl_PositionIn[0] + color;\n" +
            "    EmitVertex();\n" +
            "}\n";
        String fragment = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = entityColor;\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched = ShaderCompatibilityPatcher.patchGrouped(
            "test-pack",
            "gbuffers_entities",
            ShaderCompatibilityPatcher.patch(
                "test-pack", "gbuffers_entities", ShaderType.VERTEX, vertex, new InputAvailability(true, true, true)),
            ShaderCompatibilityPatcher.patch(
                "test-pack", "gbuffers_entities", ShaderType.GEOMETRY, geometry, new InputAvailability(true, true, true)),
            ShaderCompatibilityPatcher.patch(
                "test-pack", "gbuffers_entities", ShaderType.FRAGMENT, fragment, new InputAvailability(true, true, true)),
            new InputAvailability(true, true, true));

        assertTrue(patched.getVertexSource().contains("varying vec4 entityColor;"));
        assertTrue(patched.getVertexSource().contains("entityColor = iris_entityColor;"));
        assertTrue(patched.getGeometrySource().contains("in vec4 entityColor[];"));
        assertTrue(patched.getGeometrySource().contains("out vec4 entityColorGS;"));
        assertTrue(patched.getGeometrySource().contains("entityColorGS = entityColor[0];"));
        assertTrue(patched.getGeometrySource().contains("vec4 color = entityColor[0];"));
        assertFalse(patched.getGeometrySource().contains("uniform vec4 entityColor;"));
        assertTrue(patched.getFragmentSource().contains("varying vec4 entityColorGS;"));
        assertTrue(patched.getFragmentSource().contains("gl_FragColor = entityColorGS;"));
        assertFalse(patched.getFragmentSource().contains("uniform vec4 entityColor;"));
    }

    @Test
    public void groupedOverlayGeometryRenameRequiresInjectedFragmentInput() {
        String geometry = "#version 120\n" +
            "void main() {\n" +
            "    gl_Position = gl_PositionIn[0];\n" +
            "    EmitVertex();\n" +
            "}\n";
        String coreFragment = "#version 150 core\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = entityColor;\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched = ShaderCompatibilityPatcher.patchGrouped(
            "test-pack",
            "gbuffers_entities",
            null,
            ShaderCompatibilityPatcher.patch(
                "test-pack", "gbuffers_entities", ShaderType.GEOMETRY, geometry, new InputAvailability(true, true, true)),
            ShaderCompatibilityPatcher.patch(
                "test-pack", "gbuffers_entities", ShaderType.FRAGMENT, coreFragment,
                new InputAvailability(true, true, true)),
            new InputAvailability(true, true, true));

        assertTrue(patched.getFragmentSource().contains("uniform vec4 entityColor;"));
        assertTrue(patched.getFragmentSource().contains("gl_FragColor = entityColor;"));
        assertFalse(patched.getFragmentSource().contains("entityColorGS"));
    }

    @Test
    public void rewritesCenterDepthSmoothUniformToSamplerRead() {
        String source = "#version 120\n" +
            "#define FRAGMENT_SHADER\n" +
            "uniform float centerDepthSmooth;\n" +
            "void main() {\n" +
            "    float focus = centerDepthSmooth;\n" +
            "    gl_FragColor = vec4(focus);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertTrue(patched.contains("uniform sampler2D iris_centerDepthSmooth;\n"));
        assertFalse(patched.contains("uniform float centerDepthSmooth;"));
        assertTrue(patched.contains("float focus = texture2D(iris_centerDepthSmooth, vec2(0.5)).r;"));
    }

    @Test
    public void centerDepthSmoothRewriteRequiresUniformDeclaration() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    float centerDepthSmooth = 0.5;\n" +
            "    gl_FragColor = vec4(centerDepthSmooth);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertEquals(source, patched);
    }

    @Test
    public void doesNotRewriteCenterDepthSmoothOutsideCompositeTransformPrograms() {
        String source = "#version 120\n" +
            "uniform float centerDepthSmooth;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(centerDepthSmooth);\n" +
            "}\n";

        String gbuffers = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "gbuffers_terrain",
            ShaderType.FRAGMENT,
            source);
        String rootShadow = ShaderCompatibilityPatcher.patch(
            "test-pack",
            "shadow",
            ShaderType.FRAGMENT,
            source);

        assertEquals(source, gbuffers);
        assertEquals(source, rootShadow);
    }

    @Test
    public void ignoresCenterDepthSmoothInsideCommentsAndStrings() {
        String source = "#version 120\n" +
            "// uniform float centerDepthSmooth;\n" +
            "/* centerDepthSmooth */\n" +
            "const char marker = 'c';\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(1.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertEquals(source, patched);
    }

    @Test
    public void leavesComputeCenterDepthSmoothUntouched() {
        String source = "#version 430\n" +
            "uniform float centerDepthSmooth;\n" +
            "void main() {}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.COMPUTE, source);

        assertEquals(source, patched);
    }

    @Test
    public void injectsTextureLodExtensionForGlsl120CompositePrograms() {
        String source = "#version 120\n" +
            "#define FRAGMENT_SHADER\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2DLod(colortex0, vec2(0.5), 0.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertTrue(patched.contains("#extension GL_ARB_shader_texture_lod : require\n"));
        assertTrue(patched.indexOf("#extension GL_ARB_shader_texture_lod : require")
            < patched.indexOf("void main()"));
    }

    @Test
    public void injectsTextureLodExtensionForGlsl120FinalPrograms() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    gl_FragColor = texture3DLod(volumeTex, vec3(0.5), 0.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "final", ShaderType.FRAGMENT, source);

        assertTrue(patched.contains("#extension GL_ARB_shader_texture_lod : require\n"));
    }

    @Test
    public void injectsTextureLodExtensionEvenWhenAlreadyDeclaredLikeCompositeTransformer() {
        String source = "#version 120\n" +
            "#extension GL_ARB_shader_texture_lod : require\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2DLod(colortex0, vec2(0.5), 0.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertEquals(2, countOccurrences(patched, "#extension GL_ARB_shader_texture_lod : require"));
    }

    @Test
    public void doesNotInjectTextureLodExtensionForModernVersions() {
        String source = "#version 130\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2DLod(colortex0, vec2(0.5), 0.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertEquals(source, patched);
    }

    @Test
    public void doesNotInjectTextureLodExtensionOutsideCompositeTransformPrograms() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2DLod(tex, vec2(0.5), 0.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.FRAGMENT, source);

        assertEquals(source, patched);
    }

    @Test
    public void ignoresTextureLodNamesInsideCommentsAndStrings() {
        String source = "#version 120\n" +
            "// texture2DLod(colortex0, vec2(0.5), 0.0)\n" +
            "const char marker = 't';\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(1.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite3", ShaderType.FRAGMENT, source);

        assertEquals(source, patched);
    }

    @Test
    public void patchesSildursWaterFractInVertexShaders() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    float wave = fract( worldpos . y + 0.001 );\n" +
            "    gl_Position = vec4(wave);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("Sildurs", "gbuffers_water", ShaderType.VERTEX, source);

        assertTrue(patched.contains("float wave = fract(worldpos.y + 0.01);"));
        assertFalse(patched.contains("fract( worldpos . y + 0.001 )"));
    }

    @Test
    public void sildursWaterFractPatchIsVertexOnly() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    float wave = fract(worldpos.y + 0.001);\n" +
            "    gl_FragColor = vec4(wave);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("Sildurs", "gbuffers_water", ShaderType.FRAGMENT, source);

        assertEquals(source, patched);
    }

    @Test
    public void ignoresSildursWaterFractInsideCommentsAndStrings() {
        String source = "#version 120\n" +
            "// fract(worldpos.y + 0.001)\n" +
            "/* fract(worldpos.y + 0.001) */\n" +
            "const char marker = 'f';\n" +
            "void main() {\n" +
            "    float wave = fract(worldpos.y + 0.0015);\n" +
            "    gl_Position = vec4(wave);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("Sildurs", "gbuffers_water", ShaderType.VERTEX, source);

        assertEquals(source, patched);
    }

    @Test
    public void rewritesLegacyMidTexCoordAliasInVertexShaders() {
        String source = "#version 120\n" +
            "void main() {\n" +
            "    vec4 mid = gl_MultiTexCoord3;\n" +
            "    gl_Position = vec4(mid.xy, 0.0, 1.0);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.VERTEX, source);

        assertTrue(patched.contains("attribute vec4 mc_midTexCoord;\n"));
        assertTrue(patched.contains("vec4 mid = mc_midTexCoord;"));
        assertFalse(patched.contains("gl_MultiTexCoord3"));
    }

    @Test
    public void legacyMidTexCoordAliasIsVertexOnlyAndSkipsExistingMcMidTexCoord() {
        String fragment = "#version 120\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(gl_MultiTexCoord3.xy, 0.0, 1.0);\n" +
            "}\n";

        assertEquals(fragment,
            ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.FRAGMENT, fragment));

        String alreadyDeclared = "#version 120\n" +
            "attribute vec4 mc_midTexCoord;\n" +
            "void main() {\n" +
            "    vec4 mid = gl_MultiTexCoord3 + mc_midTexCoord;\n" +
            "    gl_Position = vec4(mid.xy, 0.0, 1.0);\n" +
            "}\n";

        assertEquals(alreadyDeclared,
            ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.VERTEX, alreadyDeclared));
    }

    @Test
    public void removesUnusedFunctionDefinitionsFromRasterShaders() {
        String source = "#version 120\n" +
            "float unused(float value) {\n" +
            "    return value * 2.0;\n" +
            "}\n" +
            "float used(float value) {\n" +
            "    return value + 1.0;\n" +
            "}\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(used(1.0));\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);

        assertFalse(patched.contains("float unused"));
        assertTrue(patched.contains("float used(float value)"));
        assertTrue(patched.contains("void main()"));
    }

    @Test
    public void removesDuplicateIdenticalFunctionDefinitionsBeforeUnusedCleanup() {
        String source = "#version 120\n" +
            "float duplicate(float value) {\n" +
            "    return value * 2.0;\n" +
            "}\n" +
            "float duplicate(float value) {\n" +
            "    return value * 2.0;\n" +
            "}\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(duplicate(1.0));\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "prepare", ShaderType.FRAGMENT, source);

        assertEquals(1, countOccurrences(patched, "float duplicate(float value)"));
        assertTrue(patched.contains("gl_FragColor = vec4(duplicate(1.0));"));
    }

    @Test
    public void leavesDifferentDuplicateFunctionBodiesForCompilerErrorVisibility() {
        String source = "#version 120\n" +
            "float duplicate(float value) {\n" +
            "    return value * 2.0;\n" +
            "}\n" +
            "float duplicate(float value) {\n" +
            "    return value * 3.0;\n" +
            "}\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(duplicate(1.0));\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "prepare", ShaderType.FRAGMENT, source);

        assertEquals(2, countOccurrences(patched, "float duplicate(float value)"));
        assertTrue(patched.contains("return value * 2.0;"));
        assertTrue(patched.contains("return value * 3.0;"));
    }

    @Test
    public void removesIdenticalDuplicateAfterDifferentConditionalBody() {
        String source = "#version 120\n" +
            "#if MC_VERSION >= 11300\n" +
            "float shifted_r_dither(vec2 frag) {\n" +
            "    return fract(dither_shift + dot(frag, vec2(0.75, 0.57)));\n" +
            "}\n" +
            "#else\n" +
            "float shifted_r_dither(vec2 frag) {\n" +
            "    return fract((frame_mod * 0.4) + dot(frag, vec2(0.75, 0.57)));\n" +
            "}\n" +
            "float shifted_r_dither(vec2 frag) {\n" +
            "    return fract((frame_mod * 0.4) + dot(frag, vec2(0.75, 0.57)));\n" +
            "}\n" +
            "#endif\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(shifted_r_dither(vec2(1.0)));\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("MakeUp-UltraFast-9.3e", "prepare", ShaderType.FRAGMENT, source);

        assertEquals(2, countOccurrences(patched, "float shifted_r_dither(vec2 frag)"));
        assertEquals(1, countOccurrences(patched, "return fract((frame_mod * 0.4)"));
        assertTrue(patched.contains("return fract(dither_shift + dot"));
    }

    @Test
    public void injectsMissingLegacyShiftedDither17WhenFrameModBranchCallsIt() {
        String source = "#version 120\n" +
            "uniform int frame_mod;\n" +
            "float dither17(vec2 pos) {\n" +
            "    return fract(dot(pos, vec2(0.11764705882352941, 0.4117647058823529)));\n" +
            "}\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(shifted_dither17(gl_FragCoord.xy));\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("MakeUp-UltraFast-9.3e", "composite",
            ShaderType.FRAGMENT, source);

        assertTrue(patched.contains("uniform int frame_mod;\nfloat shifted_dither17(vec2 pos)"));
        assertTrue(patched.contains(
            "return fract((frame_mod * 0.4) + dot(pos, vec2(0.11764705882352941, 0.4117647058823529)));"));
        assertEquals(1, countOccurrences(patched, "float shifted_dither17(vec2 pos)"));
    }

    @Test
    public void leavesExistingShiftedDither17DefinitionUntouched() {
        String source = "#version 120\n" +
            "uniform int frame_mod;\n" +
            "float shifted_dither17(vec2 pos) {\n" +
            "    return fract(dot(pos, vec2(0.11764705882352941, 0.4117647058823529)));\n" +
            "}\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(shifted_dither17(gl_FragCoord.xy));\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("MakeUp-UltraFast-9.3e", "composite",
            ShaderType.FRAGMENT, source);

        assertEquals(1, countOccurrences(patched, "float shifted_dither17(vec2 pos)"));
        assertFalse(patched.contains("frame_mod * 0.4"));
    }

    @Test
    public void keepsUnusedFunctionDefinitionsWhenNameAppearsElsewhereAndSkipsComputeShaders() {
        String source = "#version 120\n" +
            "// unused appears in a comment and should not count\n" +
            "float unused(float value) {\n" +
            "    return value * 2.0;\n" +
            "}\n" +
            "float mentioned(float value) {\n" +
            "    return value;\n" +
            "}\n" +
            "void main() {\n" +
            "    float mentioned = 1.0;\n" +
            "    gl_FragColor = vec4(mentioned);\n" +
            "}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);
        assertFalse(patched.contains("float unused"));
        assertTrue("Identifier references outside comments should keep the function like the 1.16.5 identifier index",
            patched.contains("float mentioned(float value)"));

        String compute = ShaderCompatibilityPatcher.patch("test-pack", "composite", ShaderType.COMPUTE,
            source.replace("#version 120", "#version 430"));
        assertEquals(source.replace("#version 120", "#version 430"), compute);
    }

    @Test
    public void groupedPatchAddsMissingPreviousStageOutputForUsedInput() {
        String vertex = "#version 130\n" +
            "void main() {\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "flat in vec3 sunVec;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(sunVec, 1.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_basic", vertex, null, fragment);

        assertTrue(patched.getVertexSource().contains("flat out vec3 sunVec;\n"));
        assertTrue(patched.getVertexSource().contains("sunVec = vec3(0.0);"));
        assertEquals(fragment, patched.getFragmentSource());
    }

    @Test
    public void groupedPatchLeavesUnusedInputsAndExistingAssignmentsUntouched() {
        String vertex = "#version 130\n" +
            "out vec2 texCoord;\n" +
            "void main() {\n" +
            "    texCoord = vec2(0.5);\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "in vec2 texCoord;\n" +
            "in vec4 unusedColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(texCoord, 0.0, 1.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_textured", vertex, null, fragment);

        assertEquals(vertex, patched.getVertexSource());
        assertFalse(patched.getVertexSource().contains("unusedColor"));
    }

    @Test
    public void groupedPatchInitializesDeclaredButUnassignedPreviousStageOutput() {
        String vertex = "#version 130\n" +
            "out vec2 texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "in vec2 texCoord;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(texCoord, 0.0, 1.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_textured", vertex, null, fragment);

        assertEquals(vertex.indexOf("out vec2 texCoord;"), patched.getVertexSource().indexOf("out vec2 texCoord;"));
        assertTrue(patched.getVertexSource().contains("texCoord = vec2(0.0);"));
    }

    @Test
    public void groupedPatchRepairsSingleMemberTypeMismatchWithInternalVariableAndCast() {
        String vertex = "#version 130\n" +
            "out vec2 texCoord;\n" +
            "void main() {\n" +
            "    texCoord = vec2(0.5);\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "in vec3 texCoord;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(texCoord, 1.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_textured", vertex, null, fragment);

        assertTrue(patched.getVertexSource().contains("out vec3 texCoord;"));
        assertTrue(patched.getVertexSource().contains("vec2 iris_template_texCoord;"));
        assertTrue(patched.getVertexSource().contains("iris_template_texCoord = vec2(0.5);"));
        assertTrue(patched.getVertexSource().contains("texCoord = vec3(iris_template_texCoord, vec4(0));"));
        assertFalse(patched.getVertexSource().contains("out vec2 texCoord;"));
        assertEquals(fragment, patched.getFragmentSource());
    }

    @Test
    public void groupedPatchRepairsScalarTypeMismatchAndPreservesQualifiers() {
        String vertex = "#version 130\n" +
            "flat out int materialId;\n" +
            "void main() {\n" +
            "    materialId = 2;\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "flat in float materialId;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(materialId);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_entities", vertex, null, fragment);

        assertTrue(patched.getVertexSource().contains("flat out float materialId;"));
        assertTrue(patched.getVertexSource().contains("int iris_template_materialId;"));
        assertTrue(patched.getVertexSource().contains("iris_template_materialId = 2;"));
        assertTrue(patched.getVertexSource().contains("materialId = float(iris_template_materialId);"));
        assertEquals(fragment, patched.getFragmentSource());
    }

    @Test
    public void groupedPatchSplitsMultiMemberOutputBeforeTypeMismatchRepair() {
        String vertex = "#version 130\n" +
            "out vec2 texCoord, lightCoord;\n" +
            "void main() {\n" +
            "    texCoord = vec2(0.5);\n" +
            "    lightCoord = vec2(0.8);\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "in vec3 texCoord;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(texCoord, 1.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_textured", vertex, null, fragment);

        assertTrue(patched.getVertexSource().contains("out vec3 texCoord;"));
        assertTrue(patched.getVertexSource().contains("out vec2 lightCoord;"));
        assertTrue(patched.getVertexSource().contains("vec2 iris_template_texCoord;"));
        assertTrue(patched.getVertexSource().contains("iris_template_texCoord = vec2(0.5);"));
        assertTrue(patched.getVertexSource().contains("lightCoord = vec2(0.8);"));
        assertFalse(patched.getVertexSource().contains("out vec2 texCoord, lightCoord;"));
    }

    @Test
    public void groupedPatchSkipsTypeMismatchWithDifferentDimensionalityOrExistingInternalPrefix() {
        String dimensionalMismatchVertex = "#version 130\n" +
            "out float weight;\n" +
            "void main() {\n" +
            "    weight = 1.0;\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String dimensionalMismatchFragment = "#version 130\n" +
            "in vec2 weight;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(weight, 0.0, 1.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources dimensionalMismatch =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_basic",
                dimensionalMismatchVertex, null, dimensionalMismatchFragment);

        assertEquals(dimensionalMismatchVertex, dimensionalMismatch.getVertexSource());

        String prefixedVertex = "#version 130\n" +
            "float iris_template_existing;\n" +
            "out int materialId;\n" +
            "void main() {\n" +
            "    materialId = 2;\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String prefixedFragment = "#version 130\n" +
            "in float materialId;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(materialId);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources prefixed =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_basic",
                prefixedVertex, null, prefixedFragment);

        assertEquals(prefixedVertex, prefixed.getVertexSource());
    }

    @Test
    public void groupedPatchSupportsBooleanAndReferenceNumericMissingOutputs() {
        String vertex = "#version 130\n" +
            "void main() {\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "flat in bvec2 flags;\n" +
            "in dvec3 precise;\n" +
            "void main() {\n" +
            "    gl_FragColor = flags.x ? vec4(precise, 1.0) : vec4(0.0);\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_basic", vertex, null, fragment);

        assertTrue(patched.getVertexSource().contains("flat out bvec2 flags;"));
        assertTrue(patched.getVertexSource().contains("flags = bvec2(false);"));
        assertTrue(patched.getVertexSource().contains("out dvec3 precise;"));
        assertTrue(patched.getVertexSource().contains("precise = dvec3(0.0);"));
        assertEquals(fragment, patched.getFragmentSource());
    }

    @Test
    public void groupedPatchRepairsExplicitWidthNumericTypeMismatches() {
        String vertex = "#version 130\n" +
            "flat out i16vec2 packed;\n" +
            "out uint64_t materialId;\n" +
            "out f64mat2 matrixValue;\n" +
            "void main() {\n" +
            "    packed = i16vec2(1);\n" +
            "    materialId = 1;\n" +
            "    matrixValue = f64mat2(1.0);\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "flat in f32vec3 packed;\n" +
            "in double materialId;\n" +
            "in dmat3 matrixValue;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(packed, 1.0) + vec4(float(materialId + matrixValue[0][0]));\n" +
            "}\n";

        ShaderCompatibilityPatcher.PatchedShaderSources patched =
            ShaderCompatibilityPatcher.patchGrouped("test-pack", "gbuffers_entities", vertex, null, fragment);

        assertTrue(patched.getVertexSource().contains("flat out f32vec3 packed;"));
        assertTrue(patched.getVertexSource().contains("i16vec2 iris_template_packed;"));
        assertTrue(patched.getVertexSource().contains("iris_template_packed = i16vec2(1);"));
        assertTrue(patched.getVertexSource().contains("packed = f32vec3(iris_template_packed, vec4(0));"));
        assertTrue(patched.getVertexSource().contains("out double materialId;"));
        assertTrue(patched.getVertexSource().contains("uint64_t iris_template_materialId;"));
        assertTrue(patched.getVertexSource().contains("iris_template_materialId = 1;"));
        assertTrue(patched.getVertexSource().contains("materialId = double(iris_template_materialId);"));
        assertTrue(patched.getVertexSource().contains("out dmat3 matrixValue;"));
        assertTrue(patched.getVertexSource().contains("f64mat2 iris_template_matrixValue;"));
        assertTrue(patched.getVertexSource().contains("iris_template_matrixValue = f64mat2(1.0);"));
        assertTrue(patched.getVertexSource().contains("matrixValue = dmat3(iris_template_matrixValue);"));
        assertEquals(fragment, patched.getFragmentSource());
    }

    @Test
    public void removesConstFromDeclarationInitializedByConstParameter() {
        String source = "#version 120\n" +
            "float curve(const float factor) {\n" +
            "    const float scaled = factor * 2.0;\n" +
            "    const float independent = 1.0;\n" +
            "    return scaled + independent;\n" +
            "}\n" +
            "void main() { gl_FragColor = vec4(curve(1.0)); }\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);

        assertTrue("The const parameter itself should remain const", patched.contains("curve(const float factor)"));
        assertTrue(patched.contains("float scaled = factor * 2.0;"));
        assertFalse(patched.contains("const float scaled = factor"));
        assertTrue("Literal-only declarations should keep const", patched.contains("const float independent = 1.0;"));
    }

    @Test
    public void removesConstTransitivelyFromDeclarationsUsingDerivedConstParameterValues() {
        String source = "#version 120\n" +
            "float curve(const float factor) {\n" +
            "    const float scaled = factor * 2.0;\n" +
            "    const float curved = scaled + 1.0;\n" +
            "    const float literal = 3.0;\n" +
            "    return curved + literal;\n" +
            "}\n" +
            "void main() { gl_FragColor = vec4(curve(1.0)); }\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);

        assertTrue(patched.contains("float scaled = factor * 2.0;"));
        assertTrue(patched.contains("float curved = scaled + 1.0;"));
        assertTrue(patched.contains("const float literal = 3.0;"));
    }

    @Test
    public void removesConstFromWholeMultiMemberDeclarationWhenAnyInitializerUsesConstParameter() {
        String source = "#version 120\n" +
            "float curve(const float factor) {\n" +
            "    const float scaled = factor, offset = 1.0;\n" +
            "    const float combined = offset + scaled;\n" +
            "    return combined;\n" +
            "}\n" +
            "void main() { gl_FragColor = vec4(curve(1.0)); }\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);

        assertTrue(patched.contains("float scaled = factor, offset = 1.0;"));
        assertTrue("All members of a patched declaration become tracked like the 1.16.5 transformer",
            patched.contains("float combined = offset + scaled;"));
    }

    @Test
    public void constParameterCleanupRejectsIllegalTrackedNameRedefinition() {
        String source = "#version 120\n" +
            "float curve(const float factor) {\n" +
            "    const float factor = factor + 1.0;\n" +
            "    return factor;\n" +
            "}\n" +
            "void main() { gl_FragColor = vec4(curve(1.0)); }\n";

        try {
            ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);
            fail("Expected illegal const-parameter redefinition to match the 1.16.5 transformer failure");
        } catch (IllegalStateException exception) {
            assertEquals("Illegal redefinition of const parameter factor", exception.getMessage());
        }
    }

    @Test
    public void ignoresConstParameterCleanupInsideCommentsStringsGlobalsAndComputeShaders() {
        String source = "#version 120\n" +
            "const float globalValue = 1.0;\n" +
            "float curve(const float factor) {\n" +
            "    // const float commentValue = factor;\n" +
            "    const char marker = 'c';\n" +
            "    const float literal = 2.0;\n" +
            "    return literal + factor;\n" +
            "}\n" +
            "void main() { gl_FragColor = vec4(curve(1.0)); }\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);
        assertEquals(source, patched);

        String compute = ShaderCompatibilityPatcher.patch("test-pack", "composite", ShaderType.COMPUTE,
            source.replace("#version 120", "#version 430"));
        assertEquals(source.replace("#version 120", "#version 430"), compute);
    }

    @Test
    public void removesEmptyExternalDeclarationsFromRasterShaders() {
        String source = "#version 120\n" +
            ";\n" +
            "// comment before another empty declaration\n" +
            ";\n" +
            "uniform float x;\n" +
            ";\n" +
            "void main() {\n" +
            "    for (int i = 0; i < 2; i++) {\n" +
            "        x += 1.0;\n" +
            "    }\n" +
            "}\n" +
            ";\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.VERTEX, source);

        assertTrue(patched.contains("uniform float x;"));
        assertTrue("Semicolons inside for loops must remain", patched.contains("for (int i = 0; i < 2; i++)"));
        assertFalse("Global empty declarations should be removed", patched.contains("\n;\n"));
        assertFalse("Trailing empty declaration after a function should be removed", patched.endsWith(";\n"));
    }

    @Test
    public void preservesStructBlockSemicolonWhileRemovingEmptyExternalDeclarations() {
        String source = "#version 120\n" +
            "struct Light {\n" +
            "    float strength;\n" +
            "};\n" +
            ";\n" +
            "void main() {}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_basic", ShaderType.FRAGMENT, source);

        assertTrue("Struct declaration terminator must remain", patched.contains("};\n"));
        assertFalse("Only the standalone empty declaration should be removed", patched.contains("};\n;\n"));
    }

    @Test
    public void leavesComputeEmptyExternalDeclarationsUntouched() {
        String source = "#version 430\n" +
            ";\n" +
            "layout(local_size_x = 1) in;\n" +
            "void main() {}\n";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "composite", ShaderType.COMPUTE, source);

        assertEquals(source, patched);
    }

    private static void assertCoreProfileVertexAttributeError(String source) {
        try {
            ShaderCompatibilityPatcher.patch(
                "test-pack",
                "gbuffers_textured_lit",
                ShaderType.VERTEX,
                source,
                new InputAvailability(true, true, false));
            fail("Expected core-profile vertex attribute transform to throw");
        } catch (IllegalStateException exception) {
            assertEquals(CORE_PROFILE_VERTEX_SHADER_ERROR, exception.getMessage());
        }
    }

    private static void assertInternalInterfaceError(String source, String identifier) {
        try {
            ShaderCompatibilityPatcher.patch("test-pack", "gbuffers_terrain", ShaderType.VERTEX, source);
            fail("Expected internal Iris interface guard to throw");
        } catch (IllegalStateException exception) {
            assertEquals(INTERNAL_INTERFACE_ERROR_PREFIX + identifier + INTERNAL_INTERFACE_ERROR_SUFFIX,
                exception.getMessage());
        }
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while (index < source.length()) {
            int found = source.indexOf(needle, index);
            if (found < 0) {
                return count;
            }
            count++;
            index = found + needle.length();
        }
        return count;
    }
}

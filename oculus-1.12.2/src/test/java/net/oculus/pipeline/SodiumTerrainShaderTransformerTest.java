package net.oculus.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.oculus.gl.shader.ShaderType;
import org.junit.Test;

public class SodiumTerrainShaderTransformerTest {
    private static final String INTERNAL_INTERFACE_ERROR_PREFIX =
        "Detected a potential reference to unstable and internal Iris shader interfaces (iris_ and irisMain). "
            + "This isn't currently supported. Violation: ";
    private static final String INTERNAL_INTERFACE_ERROR_SUFFIX = ". See debugging.md for more information.";
    private static final String MISSING_VERSION_ERROR =
        "No #version directive found in source code! See debugging.md for more information.";

    @Test
    public void vertexTransformInjectsRelictiumAttributesAndRewritesFixedFunctionInputs() {
        String source = "#version 120\n"
            + "void main() {\n"
            + "    gl_Position = ftransform();\n"
            + "    vec4 pos = gl_Vertex;\n"
            + "    vec4 uv0 = gl_MultiTexCoord0;\n"
            + "    vec4 uv1 = gl_MultiTexCoord1 + gl_MultiTexCoord2;\n"
            + "    vec4 color = gl_Color;\n"
            + "    vec3 normal = gl_Normal;\n"
            + "    vec4 world = gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex;\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);

        assertTrue(transformed.contains("attribute vec3 iris_Pos;"));
        assertTrue(transformed.contains("attribute vec4 iris_Color;"));
        assertTrue(transformed.contains("attribute vec2 iris_TexCoord;"));
        assertTrue(transformed.contains("attribute vec2 iris_LightCoord;"));
        assertTrue(transformed.contains("attribute vec3 iris_Normal;"));
        assertTrue(transformed.contains("attribute vec4 iris_ModelOffset;"));
        assertTrue(transformed.contains("uniform vec3 u_ModelScale;"));
        assertTrue(transformed.contains("uniform vec2 u_TextureScale;"));
        assertTrue(transformed.contains("vec4 iris_LightTexCoord = vec4(iris_LightCoord, 0, 1);"));
        assertTrue(transformed.contains("vec4 iris_ftransform()"));
        assertTrue(transformed.contains("gl_Position = iris_ftransform();"));
        assertTrue(transformed.contains("vec4 pos = vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0);"));
        assertTrue(transformed.contains("vec4 uv0 = vec4(iris_TexCoord * u_TextureScale, 0.0, 1.0);"));
        assertTrue(transformed.contains("vec4 uv1 = iris_LightTexCoord + iris_LightTexCoord;"));
        assertTrue(transformed.contains("vec4 color = iris_Color;"));
        assertTrue(transformed.contains("vec3 normal = iris_Normal;"));
        assertTrue(transformed.contains("vec4 world = vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0);"));
        assertFalse(transformed.contains("gbufferModelViewInverse * iris_ModelViewMatrix * vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0)"));
    }

    @Test
    public void sharedTransformRewritesMatricesAndTextureMatrices() {
        String source = "#version 120\n"
            + "void main() {\n"
            + "    mat4 mv = gl_ModelViewMatrix;\n"
            + "    mat4 proj = gl_ProjectionMatrix;\n"
            + "    mat4 mvp = gl_ModelViewProjectionMatrix;\n"
            + "    mat3 normal = gl_NormalMatrix;\n"
            + "    vec4 tex0 = gl_TextureMatrix[0] * vec4(1.0);\n"
            + "    vec4 tex1 = gl_TextureMatrix [ 1 ] * vec4(1.0);\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.FRAGMENT, source);

        assertTrue(transformed.contains("uniform mat4 iris_ModelViewMatrix;"));
        assertTrue(transformed.contains("uniform mat4 iris_ProjectionMatrix;"));
        assertTrue(transformed.contains("uniform mat4 u_ModelViewProjectionMatrix;"));
        assertTrue(transformed.contains("uniform mat4 iris_NormalMatrix;"));
        assertTrue(transformed.contains("uniform mat4 iris_LightmapTextureMatrix;"));
        assertTrue(transformed.contains("mat4 mv = iris_ModelViewMatrix;"));
        assertTrue(transformed.contains("mat4 proj = iris_ProjectionMatrix;"));
        assertTrue(transformed.contains("mat4 mvp = u_ModelViewProjectionMatrix;"));
        assertTrue(transformed.contains("mat3 normal = mat3(iris_NormalMatrix);"));
        assertTrue(transformed.contains("vec4 tex0 = mat4(1.0) * vec4(1.0);"));
        assertTrue(transformed.contains("vec4 tex1 = iris_LightmapTextureMatrix * vec4(1.0);"));
    }

    @Test
    public void transformSkipsCommentsStringsAndPreprocessorLines() {
        String source = "#version 120\n"
            + "/* header gl_Vertex */\n"
            + " * gl_Color\n"
            + " */\n"
            + "#define KEEP gl_ModelViewMatrix gl_Vertex\n"
            + "#define KEEP_SANDWICH gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex\n"
            + "// gl_Vertex gl_TextureMatrix[1] ftransform\n"
            + "// gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex\n"
            + "void main() {\n"
            + "    const char* text = \"gl_Vertex gl_ModelViewMatrix\";\n"
            + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);

        assertTrue(transformed.contains("#define KEEP gl_ModelViewMatrix gl_Vertex"));
        assertTrue(transformed.contains("#define KEEP_SANDWICH gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex"));
        assertTrue(transformed.contains("// gl_Vertex gl_TextureMatrix[1] ftransform"));
        assertTrue(transformed.contains("// gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex"));
        assertTrue(transformed.contains("\"gl_Vertex gl_ModelViewMatrix\""));
        assertTrue(transformed.contains("gl_Position = u_ModelViewProjectionMatrix * vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0);"));
        assertTrue(transformed.indexOf("attribute vec3 iris_Pos;") < transformed.indexOf("/* header gl_Vertex */"));
        assertFalse(transformed.contains("gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;"));
    }

    @Test
    public void transformInjectsGeneratedDeclarationsBeforeLeadingBannerComments() {
        String source = "#version 120\n"
            + "#define SHADOW\n"
            + "\n"
            + "/////////////////////////////////////\n"
            + "// shader banner\n"
            + "/////////////////////////////////////\n"
            + "\n"
            + "/*---------------------------------------------------------------------\n"
            + "  banner text\n"
            + "---------------------------------------------------------------------*/\n"
            + "void main() {\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);

        int declarations = transformed.indexOf("attribute vec3 iris_Pos;");
        int lineComment = transformed.indexOf("/////////////////////////////////////");
        int blockComment = transformed.indexOf("/*---------------------------------------------------------------------");
        int blockCommentEnd = transformed.indexOf("---------------------------------------------------------------------*/", blockComment);

        assertTrue("Generated declarations must exist", declarations >= 0);
        assertTrue("Generated declarations must not be injected into leading line or block comments",
            declarations < lineComment && declarations < blockComment);
        assertFalse("Generated ftransform helper must not be inside the banner block comment",
            transformed.substring(blockComment, blockCommentEnd).contains("iris_ftransform"));
        assertTrue(transformed.contains("gl_Position = iris_ftransform();"));
    }

    @Test
    public void transformInjectsGeneratedDeclarationsEvenWhenPackDeclaredSameNameLikeReference() {
        String source = "#version 120\n"
            + "uniform vec3 u_ModelScale;\n"
            + "void main() {\n"
            + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);

        assertEquals(2, countOccurrences(transformed, "uniform vec3 u_ModelScale;"));
        assertTrue(transformed.contains("gl_Position = u_ModelViewProjectionMatrix * vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0);"));
    }

    @Test
    public void transformInjectsGeneratedDeclarationsWhenPackOnlyReferencedSameName() {
        String source = "#version 120\n"
            + "vec3 readScale() {\n"
            + "    return u_ModelScale;\n"
            + "}\n"
            + "void main() {\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);

        assertTrue(transformed.contains("uniform vec3 u_ModelScale;"));
        assertTrue(transformed.contains("return u_ModelScale;"));
    }

    @Test
    public void internalIrisInterfaceReferenceThrowsLikeTransformPatcher() {
        String source = "#version 120\n"
            + "void main() {\n"
            + "    vec3 pos = iris_Pos;\n"
            + "}\n";

        assertInternalInterfaceError(source, "iris_Pos");
    }

    @Test
    public void internalIrisMainReferenceThrowsLikeTransformPatcher() {
        String source = "#version 120\n"
            + "void irisMain() {\n"
            + "}\n";

        assertInternalInterfaceError(source, "irisMain");
    }

    @Test
    public void internalInterfaceGuardSkipsCommentsAndStrings() {
        String source = "#version 120\n"
            + "// iris_Pos\n"
            + "/* irisMain */\n"
            + "void main() {\n"
            + "    const char* text = \"iris_Pos irisMain\";\n"
            + "    gl_Position = gl_Vertex;\n"
            + "}\n";

        String transformed = SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);

        assertTrue(transformed.contains("// iris_Pos"));
        assertTrue(transformed.contains("/* irisMain */"));
        assertTrue(transformed.contains("\"iris_Pos irisMain\""));
        assertTrue(transformed.contains("gl_Position = vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0);"));
    }

    @Test
    public void missingVersionDirectiveThrowsLikeTransformPatcher() {
        try {
            SodiumTerrainShaderTransformer.transform(
                ShaderType.VERTEX,
                "void main() {\n"
                    + "    gl_Position = gl_Vertex;\n"
                    + "}\n");
            fail("Expected Sodium terrain transform to require an explicit #version directive");
        } catch (IllegalArgumentException exception) {
            assertEquals(MISSING_VERSION_ERROR, exception.getMessage());
        }
    }

    @Test
    public void malformedVersionDirectiveThrowsLikeTransformPatcher() {
        try {
            SodiumTerrainShaderTransformer.transform(
                ShaderType.VERTEX,
                "#version compatibility\n"
                    + "void main() {\n"
                    + "    gl_Position = gl_Vertex;\n"
                    + "}\n");
            fail("Expected Sodium terrain transform to require a numeric #version directive");
        } catch (IllegalArgumentException exception) {
            assertEquals(MISSING_VERSION_ERROR, exception.getMessage());
        }
    }

    @Test
    public void computeShaderTypeThrowsLikeReferenceTransformer() {
        try {
            SodiumTerrainShaderTransformer.transform(ShaderType.COMPUTE, "#version 430\nvoid main() {}\n");
            fail("Expected compute terrain transform to throw");
        } catch (IllegalStateException exception) {
            assertEquals("Unexpected Sodium terrain patching shader type: COMPUTE", exception.getMessage());
        }
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void assertInternalInterfaceError(String source, String identifier) {
        try {
            SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, source);
            fail("Expected internal Iris interface guard to throw");
        } catch (IllegalStateException exception) {
            assertEquals(INTERNAL_INTERFACE_ERROR_PREFIX + identifier + INTERNAL_INTERFACE_ERROR_SUFFIX,
                exception.getMessage());
        }
    }
}

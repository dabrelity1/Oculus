package net.oculus.shader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import net.oculus.gl.shader.ShaderType;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.include.AbsolutePackPath;
import org.junit.Test;

public class ShaderPreprocessorTest {
    @Test
    public void colorSpaceDefinesAreExposedWhenPackSupportsColorCorrection() {
        ShaderProperties properties = new ShaderProperties("supportsColorCorrection=true\n");
        ProgramSet programSet = new ProgramSet(AbsolutePackPath.fromAbsolutePath("/"),
            path -> null, properties, ShaderPack.internal());

        List<StringPair> defines = ShaderPreprocessor.buildColorSpaceDefines(programSet);

        assertTrue(contains(defines, "COLOR_SPACE_SRGB", "0"));
        assertTrue(contains(defines, "COLOR_SPACE_ADOBE_RGB", "4"));
    }

    @Test
    public void colorSpaceDefinesAreOmittedByDefault() {
        ProgramSet programSet = new ProgramSet(AbsolutePackPath.fromAbsolutePath("/"),
            path -> null, ShaderProperties.empty(), ShaderPack.internal());

        List<StringPair> defines = ShaderPreprocessor.buildColorSpaceDefines(programSet);

        assertFalse(containsName(defines, "COLOR_SPACE_SRGB"));
    }

    @Test
    public void vaPositionAliasIsVec3ForComplementaryLineRendering() {
        List<StringPair> defines = ShaderPreprocessor.buildLegacyCompatibilityDefines();
        String source = "#version 120\n" +
            "void main() {\n" +
            "    vec4 linePosStart = projectionMatrix * modelViewMatrix * vec4(vaPosition, 1.0);\n" +
            "    vec4 linePosEnd = projectionMatrix * modelViewMatrix * vec4(vaPosition + vaNormal, 1.0);\n" +
            "}\n";

        String prepared = ShaderPreprocessor.applyDefines(source, ShaderType.VERTEX, "gbuffers_line", defines);

        assertTrue(contains(defines, "vaPosition", "gl_Vertex.xyz"));
        assertFalse(contains(defines, "vaPosition", "gl_Vertex"));
        assertTrue(prepared.contains("#define vaPosition gl_Vertex.xyz\n"));
        assertTrue(prepared.contains("#define vaNormal gl_Normal\n"));
        assertTrue(prepared.contains("#define GBUFFERS_LINE\n"));
    }

    private static boolean contains(List<StringPair> defines, String name, String value) {
        for (StringPair pair : defines) {
            if (name.equals(pair.getKey()) && value.equals(pair.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsName(List<StringPair> defines, String name) {
        for (StringPair pair : defines) {
            if (name.equals(pair.getKey())) {
                return true;
            }
        }
        return false;
    }
}

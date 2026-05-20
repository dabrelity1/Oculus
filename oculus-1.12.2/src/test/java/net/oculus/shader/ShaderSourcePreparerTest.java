package net.oculus.shader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.InputAvailability;
import org.junit.Test;

public class ShaderSourcePreparerTest {
    private static final String MISSING_VERSION_ERROR =
        "No #version directive found in source code! See debugging.md for more information.";

    @Test
    public void prepareProgramAppliesGroupedInterfacePatchesAfterStagePreparation() {
        String vertex = "#version 130\n" +
            "void main() {\n" +
            "    gl_Position = vec4(1.0);\n" +
            "}\n";
        String fragment = "#version 130\n" +
            "in vec3 sunVec;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(sunVec, 1.0);\n" +
            "}\n";

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            "test-pack",
            "gbuffers_basic",
            vertex,
            null,
            fragment,
            Collections.emptyList());

        assertTrue(prepared.getVertexSource().contains("#define VERTEX_SHADER\n"));
        assertTrue(prepared.getVertexSource().contains("out vec3 sunVec;\n"));
        assertTrue(prepared.getVertexSource().contains("sunVec = vec3(0.0);"));
        assertTrue(prepared.getFragmentSource().contains("#define FRAGMENT_SHADER\n"));
    }

    @Test
    public void prepareProgramUsesAvailabilitySpecificAttributePatches() {
        String vertex = "#version 120\n" +
            "void main() {\n" +
            "    gl_Position = gl_MultiTexCoord2;\n" +
            "}\n";
        String fragment = "#version 120\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(1.0);\n" +
            "}\n";

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            "test-pack",
            "gbuffers_textured_lit",
            vertex,
            null,
            fragment,
            Collections.emptyList(),
            new InputAvailability(true, true, false));

        assertTrue(prepared.getVertexSource().contains("gl_Position = gl_MultiTexCoord1;"));
    }

    @Test
    public void prepareProgramPassesOverlayAvailabilityToGroupedEntityColorPatch() {
        String vertex = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_Position = gl_Vertex;\n" +
            "}\n";
        String geometry = "#version 120\n" +
            "void main() {\n" +
            "    gl_Position = gl_PositionIn[0];\n" +
            "    EmitVertex();\n" +
            "}\n";
        String fragment = "#version 120\n" +
            "uniform vec4 entityColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = entityColor;\n" +
            "}\n";

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            "test-pack",
            "gbuffers_entities",
            vertex,
            geometry,
            fragment,
            Collections.emptyList(),
            new InputAvailability(true, true, true));

        assertTrue(prepared.getVertexSource().contains("uniform vec4 iris_entityColor;"));
        assertTrue(prepared.getGeometrySource().contains("out vec4 entityColorGS;"));
        assertTrue(prepared.getFragmentSource().contains("varying vec4 entityColorGS;"));
        assertTrue(prepared.getFragmentSource().contains("gl_FragColor = entityColorGS;"));
    }

    @Test
    public void prepareRequiresExplicitVersionBeforeCompatibilityInjection() {
        try {
            ShaderSourcePreparer.prepare(
                "test-pack",
                "gbuffers_basic",
                ShaderType.VERTEX,
                "void main() { gl_Position = gl_Vertex; }\n",
                Collections.emptyList());
            fail("Expected source preparation to require an explicit #version directive");
        } catch (IllegalArgumentException exception) {
            assertEquals(MISSING_VERSION_ERROR, exception.getMessage());
        }
    }

    @Test
    public void prepareRejectsMalformedVersionBeforeCompatibilityInjection() {
        try {
            ShaderSourcePreparer.prepare(
                "test-pack",
                "gbuffers_basic",
                ShaderType.FRAGMENT,
                "#version compatibility\nvoid main() { gl_FragColor = vec4(1.0); }\n",
                Collections.emptyList());
            fail("Expected source preparation to require a numeric #version directive");
        } catch (IllegalArgumentException exception) {
            assertEquals(MISSING_VERSION_ERROR, exception.getMessage());
        }
    }

    @Test
    public void prepareLeavesComputeVersionHandlingToComputeCompilerPath() {
        String prepared = ShaderSourcePreparer.prepare(
            "test-pack",
            "composite",
            ShaderType.COMPUTE,
            "void main() {}\n",
            Collections.emptyList());

        assertTrue(prepared.contains("#define COMPUTE_SHADER\n"));
        assertTrue(prepared.contains("void main() {}\n"));
    }
}

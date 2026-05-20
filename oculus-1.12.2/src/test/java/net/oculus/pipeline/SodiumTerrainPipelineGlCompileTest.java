package net.oculus.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.oculus.shader.ShaderPreprocessor;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderPackLoader;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import org.junit.Assume;
import org.junit.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

public class SodiumTerrainPipelineGlCompileTest {
    private static final String COMPLEMENTARY_ZIP = "ComplementaryReimagined_r5.6.1.zip";
    private static final String ENABLE_GL_COMPILE_TEST_PROPERTY = "oculus.tests.sodiumTerrainGl";

    @Test
    public void complementaryRuntimeTerrainOverrideSourcesCompileAndLinkWithOpenGl() throws Exception {
        assumeGlCompileTestEnabled();

        Path shaderpacks = Paths.get("run", "shaderpacks");
        Assume.assumeTrue("Complementary Reimagined zip is not available",
            Files.isRegularFile(shaderpacks.resolve(COMPLEMENTARY_ZIP)));

        assumeDisplayAvailableBeforeLwjgl();
        Pbuffer pbuffer = createOpenGlContextOrSkip();
        try {
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

            linkProgram(
                pipeline.getTerrainProgramName().get(),
                pipeline.getTerrainVertexShaderSource().get(),
                pipeline.getTerrainGeometryShaderSource().orElse(null),
                pipeline.getTerrainFragmentShaderSource().get());
            linkProgram(
                pipeline.getTranslucentProgramName().get(),
                pipeline.getTranslucentVertexShaderSource().get(),
                pipeline.getTranslucentGeometryShaderSource().orElse(null),
                pipeline.getTranslucentFragmentShaderSource().get());
            linkProgram(
                pipeline.getShadowProgramName().get(),
                pipeline.getShadowVertexShaderSource().get(),
                pipeline.getShadowGeometryShaderSource().orElse(null),
                pipeline.getShadowFragmentShaderSource().get());
        } finally {
            pbuffer.destroy();
        }
    }

    private static Pbuffer createOpenGlContextOrSkip() {
        assumeGlCompileTestEnabled();
        assumeDisplayAvailableBeforeLwjgl();
        try {
            Assume.assumeTrue("Pbuffers are not supported by the local LWJGL/OpenGL environment",
                (Pbuffer.getCapabilities() & Pbuffer.PBUFFER_SUPPORTED) != 0);
            Pbuffer pbuffer = new Pbuffer(16, 16, new PixelFormat(), null);
            pbuffer.makeCurrent();
            Assume.assumeTrue("OpenGL 2.0 is required to compile shader-pack programs",
                GLContext.getCapabilities().OpenGL20);
            return pbuffer;
        } catch (LWJGLException | LinkageError | RuntimeException exception) {
            Assume.assumeNoException("No local OpenGL context is available for shader compilation", exception);
            throw new AssertionError("JUnit assumption should have skipped the test", exception);
        }
    }

    private static void assumeGlCompileTestEnabled() {
        Assume.assumeTrue("Sodium terrain GL compile/link test is opt-in; set -D"
            + ENABLE_GL_COMPILE_TEST_PROPERTY + "=true when a working LWJGL display context is available",
            Boolean.getBoolean(ENABLE_GL_COMPILE_TEST_PROPERTY));
    }

    private static void assumeDisplayAvailableBeforeLwjgl() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("linux")) {
            return;
        }

        Assume.assumeTrue("Skipping LWJGL Pbuffer shader compile test because no DISPLAY/WAYLAND_DISPLAY is available",
            hasText(System.getenv("DISPLAY")) || hasText(System.getenv("WAYLAND_DISPLAY")));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void linkProgram(String name, String vertexSource, String geometrySource, String fragmentSource) {
        int vertexShader = 0;
        int geometryShader = 0;
        int fragmentShader = 0;
        int program = 0;

        try {
            vertexShader = compileShader(name + ".vsh", GL20.GL_VERTEX_SHADER, vertexSource);
            if (geometrySource != null) {
                Assume.assumeTrue("OpenGL 3.2 is required for geometry shader compile coverage",
                    GLContext.getCapabilities().OpenGL32);
                geometryShader = compileShader(name + ".gsh", 0x8DD9, geometrySource);
            }
            fragmentShader = compileShader(name + ".fsh", GL20.GL_FRAGMENT_SHADER, fragmentSource);

            program = GL20.glCreateProgram();
            bindTerrainAttributes(program);
            GL20.glAttachShader(program, vertexShader);
            if (geometryShader != 0) {
                GL20.glAttachShader(program, geometryShader);
            }
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);

            int status = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
            String log = getProgramLog(program);
            assertEquals("Failed to link " + name + "\n" + log, GL11.GL_TRUE, status);
        } finally {
            detach(program, vertexShader);
            detach(program, geometryShader);
            detach(program, fragmentShader);
            deleteShader(vertexShader);
            deleteShader(geometryShader);
            deleteShader(fragmentShader);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
        }
    }

    private static int compileShader(String name, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        int status = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        String log = getShaderLog(shader);
        assertEquals("Failed to compile " + name + "\n" + log, GL11.GL_TRUE, status);
        return shader;
    }

    private static void bindTerrainAttributes(int program) {
        GL20.glBindAttribLocation(program, 0, "iris_Pos");
        GL20.glBindAttribLocation(program, 1, "iris_Color");
        GL20.glBindAttribLocation(program, 2, "iris_TexCoord");
        GL20.glBindAttribLocation(program, 3, "iris_LightCoord");
        GL20.glBindAttribLocation(program, 4, "iris_ModelOffset");
        GL20.glBindAttribLocation(program, 5, "iris_Normal");
        GL20.glBindAttribLocation(program, 6, "at_tangent");
        GL20.glBindAttribLocation(program, 7, "mc_midTexCoord");
        GL20.glBindAttribLocation(program, 8, "mc_Entity");
        GL20.glBindAttribLocation(program, 9, "at_midBlock");
    }

    private static String getShaderLog(int shader) {
        int logLength = GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH);
        return logLength <= 0 ? "" : GL20.glGetShaderInfoLog(shader, logLength);
    }

    private static String getProgramLog(int program) {
        int logLength = GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH);
        return logLength <= 0 ? "" : GL20.glGetProgramInfoLog(program, logLength);
    }

    private static void detach(int program, int shader) {
        if (program != 0 && shader != 0) {
            GL20.glDetachShader(program, shader);
        }
    }

    private static void deleteShader(int shader) {
        if (shader != 0) {
            GL20.glDeleteShader(shader);
        }
    }

    private static Map<String, String> complementaryRuntimeOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("COLORED_LIGHTING", "128");
        overrides.put("WORLD_SPACE_REFLECTIONS", "1");
        return overrides;
    }
}

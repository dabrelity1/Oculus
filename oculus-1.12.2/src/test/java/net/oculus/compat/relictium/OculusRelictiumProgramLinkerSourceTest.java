package net.oculus.compat.relictium;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusRelictiumProgramLinkerSourceTest {
    @Test
    public void relictiumLinkerBindingsMatchExtendedTerrainVertexSurface() throws Exception {
        String linker = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumProgramLinker.java");
        String bindingHelper = read("src/main/java/net/oculus/pipeline/OculusVertexBindingHelper.java");
        String vertexType = read("src/main/java/net/oculus/pipeline/OculusTerrainVertexType.java");

        assertTrue(linker.contains("bind(program, OculusChunkShaderBindingPoints.NORMAL, \"iris_Normal\");"));
        assertTrue(linker.contains("bind(program, OculusChunkShaderBindingPoints.TANGENT, \"at_tangent\");"));
        assertTrue(linker.contains("bind(program, OculusChunkShaderBindingPoints.MID_UV, \"mc_midTexCoord\");"));
        assertTrue(linker.contains("bind(program, OculusChunkShaderBindingPoints.BLOCK_ID, \"mc_Entity\");"));
        assertTrue(linker.contains("bind(program, OculusChunkShaderBindingPoints.MID_BLOCK, \"at_midBlock\");"));

        assertTrue(bindingHelper.contains(
            "maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.NORMAL, OculusChunkMeshAttributes.NORMAL);"));
        assertTrue(bindingHelper.contains(
            "maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.TANGENT, OculusChunkMeshAttributes.TANGENT);"));
        assertTrue(bindingHelper.contains(
            "maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.MID_UV, OculusChunkMeshAttributes.MID_UV);"));
        assertTrue(bindingHelper.contains(
            "maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.BLOCK_ID, OculusChunkMeshAttributes.MATERIAL);"));
        assertTrue(bindingHelper.contains(
            "maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.MID_BLOCK, OculusChunkMeshAttributes.MID_BLOCK);"));

        assertTrue(vertexType.contains(
            ".addElement(OculusChunkMeshAttributes.NORMAL, 28, OculusGlVertexAttributeFormats.BYTE, 4, true)"));
        assertTrue(vertexType.contains(
            ".addElement(OculusChunkMeshAttributes.MATERIAL, 32, OculusGlVertexAttributeFormats.SHORT, 2, false)"));
        assertTrue(vertexType.contains(
            ".addElement(OculusChunkMeshAttributes.MID_UV, 36, GlVertexAttributeFormat.FLOAT, 2, false)"));
        assertTrue(vertexType.contains(
            ".addElement(OculusChunkMeshAttributes.TANGENT, 44, OculusGlVertexAttributeFormats.BYTE, 4, true)"));
        assertTrue(vertexType.contains(
            ".addElement(OculusChunkMeshAttributes.MID_BLOCK, 48, OculusGlVertexAttributeFormats.BYTE, 4, false)"));
    }

    @Test
    public void linkerIsolatesCleanupFailuresFromLinkResultAndPrimaryFailures() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumProgramLinker.java");
        String link = methodBody(source,
            "public static int link(String name, String vertexSource, String geometrySource, String fragmentSource)");
        String detach = methodBody(source,
            "private static void detach(int program, GlShader shader, String name, Throwable primaryFailure)");
        String destroy = methodBody(source,
            "private static void destroy(GlShader shader, String name, Throwable primaryFailure)");
        String deleteFailed = methodBody(source,
            "private static void deleteFailedProgram(int program, String name, Throwable primaryFailure)");
        String cleanup = methodBody(source,
            "private static void handleCleanupFailure(Throwable primaryFailure, Throwable cleanupFailure, String message)");

        int primary = link.indexOf("Throwable primaryFailure = null;");
        int linkedTrue = link.indexOf("linked = true;", primary);
        int returnProgram = link.indexOf("return program;", linkedTrue);
        int catchBlock = link.indexOf("catch (RuntimeException | Error exception)", returnProgram);
        int setPrimary = link.indexOf("primaryFailure = exception;", catchBlock);
        int rethrow = link.indexOf("throw exception;", setPrimary);
        int detachVertex = link.indexOf("detach(program, vertexShader, name, primaryFailure);", rethrow);
        int detachGeometry = link.indexOf("detach(program, geometryShader, name, primaryFailure);", detachVertex);
        int detachFragment = link.indexOf("detach(program, fragmentShader, name, primaryFailure);", detachGeometry);
        int destroyVertex = link.indexOf("destroy(vertexShader, name, primaryFailure);", detachFragment);
        int deleteFailedProgram = link.indexOf("deleteFailedProgram(program, name, primaryFailure);", destroyVertex);

        assertTrue("Link failures should be retained as the primary failure", primary >= 0);
        assertTrue("Successful links must mark linked before returning", linkedTrue > primary);
        assertTrue("The linked handle should still return after cleanup", returnProgram > linkedTrue);
        assertTrue("Runtime and hard link failures should be captured", catchBlock > returnProgram);
        assertTrue("Primary failure should be available to cleanup handlers", setPrimary > catchBlock);
        assertTrue("The original link failure should be rethrown", rethrow > setPrimary);
        assertTrue("Vertex shader detach should use isolated cleanup", detachVertex > rethrow);
        assertTrue("Geometry shader detach should use isolated cleanup", detachGeometry > detachVertex);
        assertTrue("Fragment shader detach should use isolated cleanup", detachFragment > detachGeometry);
        assertTrue("Shader destroy should use isolated cleanup", destroyVertex > detachFragment);
        assertTrue("Failed linked programs should delete through isolated cleanup", deleteFailedProgram > destroyVertex);

        assertTrue("Detach cleanup should be isolated",
            detach.contains("catch (RuntimeException | Error cleanupFailure)") &&
                detach.contains("handleCleanupFailure(primaryFailure, cleanupFailure"));
        assertTrue("Shader object destroy cleanup should be isolated",
            destroy.contains("catch (RuntimeException | Error cleanupFailure)") &&
                destroy.contains("handleCleanupFailure(primaryFailure, cleanupFailure"));
        assertTrue("Failed program delete cleanup should be isolated",
            deleteFailed.contains("catch (RuntimeException | Error cleanupFailure)") &&
                deleteFailed.contains("handleCleanupFailure(primaryFailure, cleanupFailure"));
        assertTrue("Cleanup failures should be suppressed onto the original link failure",
            cleanup.contains("if (primaryFailure != null)") &&
                cleanup.contains("suppressCleanupFailure(primaryFailure, cleanupFailure);"));
        assertTrue("Cleanup failures without a primary link failure should be logged, not thrown",
            cleanup.contains("LOGGER.debug(message, cleanupFailure);"));
        assertFalse("Raw Relictium detach should not run from the link finally block",
            link.contains("OculusRenderSystem.glDetachShader(program,"));
        assertFalse("Raw failed-program delete should not run from the link finally block",
            link.contains("OculusRenderSystem.glDeleteProgram(program);"));
    }

    @Test
    public void cleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same linker cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void cleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("link failure");
        RuntimeException cleanupFailure = new RuntimeException("link cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = OculusRelictiumProgramLinker.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method signature " + signature, signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body for " + signature, bodyStart >= 0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, index);
                }
            }
        }

        throw new AssertionError("Unterminated method body for " + signature);
    }
}

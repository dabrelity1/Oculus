package net.oculus.gl.shader;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ProgramCreatorSourceTest {
    @Test
    public void cleanupFailuresDoNotMaskProgramCreateFailure() throws IOException {
        String source = read("src/main/java/net/oculus/gl/shader/ProgramCreator.java");

        assertTrue(source.contains("Throwable primaryFailure = null;"));
        assertTrue(source.contains("} catch (RuntimeException | Error exception) {\n"
            + "            primaryFailure = exception;\n"
            + "            throw exception;\n"
            + "        } finally {"));
        assertTrue(source.contains("detachShader(program, shader, name, primaryFailure);"));
        assertTrue(source.contains("deleteFailedProgram(program, name, primaryFailure);"));
        assertTrue(source.contains("primaryFailure.addSuppressed(cleanupFailure);"));
        assertTrue(source.contains("LOGGER.debug(message, cleanupFailure);"));
    }

    @Test
    public void cleanupHelpersCatchRuntimeFailuresAndHardErrors() throws IOException {
        String source = read("src/main/java/net/oculus/gl/shader/ProgramCreator.java");

        assertTrue(source.contains("private static void detachShader(int program, GlShader shader, String name, Throwable primaryFailure)"));
        assertTrue(source.contains("OculusRenderSystem.glDetachShader(program, shader.getHandle());"));
        assertTrue(source.contains("private static void deleteFailedProgram(int program, String name, Throwable primaryFailure)"));
        assertTrue(source.contains("OculusRenderSystem.glDeleteProgram(program);"));
        assertTrue(source.contains("} catch (RuntimeException | Error cleanupFailure) {\n"
            + "            handleCleanupFailure(primaryFailure, cleanupFailure,"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

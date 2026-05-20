package net.oculus.pipeline.buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.oculus.shaderpack.ShaderProperties;
import org.junit.Test;

public class ShaderStorageBufferManagerTest {
    @Test
    public void unavailableSupportFailsClearlyForRequestedBuffers() {
        ShaderStorageBufferManager manager = new ShaderStorageBufferManager(
            new ShaderProperties("bufferObject.0 = 16\n"),
            () -> false);

        try {
            manager.initialize();
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("shader storage buffers"));
            assertTrue(exception.getMessage().contains("SSBOs"));
            assertFalse(manager.isInitialized());
            return;
        }

        throw new AssertionError("Expected unsupported shader storage buffers to fail clearly");
    }

    @Test
    public void requestedBufferFailuresAreNotSilentSkips() throws IOException {
        String source = readManagerSource();

        assertTrue(source.contains("Shader pack declares shader storage buffers, but SSBOs are not supported"));
        assertTrue(source.contains("Shader pack requested shader storage buffer binding "));
        assertTrue(source.contains("with invalid size "));
        assertTrue(source.contains("Failed to allocate shader storage buffer at binding "));
        assertFalse(source.contains("warnUnsupported"));
        assertFalse(source.contains("Ignoring shader storage buffer"));
    }

    @Test
    public void initializeIsOnlyMarkedAfterRequestedBuffersArePrepared() throws IOException {
        String source = readManagerSource();
        String initializeBody = methodBody(source, "public void initialize()");

        int allocation = initializeBody.indexOf("GL15.glGenBuffers()");
        int initialized = initializeBody.lastIndexOf("initialized = true;");

        assertTrue(allocation >= 0);
        assertTrue(initialized > allocation);
    }

    @Test
    public void initializeRestoresPreviousGenericSsboBindingAfterAllocation() throws IOException {
        String source = readManagerSource();
        String initializeBody = methodBody(source, "public void initialize()");

        int generated = initializeBody.indexOf("int buffer = GL15.glGenBuffers();");
        int put = initializeBody.indexOf("buffers.put(binding, buffer);", generated);
        int capturePrevious = initializeBody.indexOf(
            "int previousBuffer = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER_BINDING);",
            put);
        int bindGenerated = initializeBody.indexOf("GL15.glBindBuffer(TARGET, buffer);", capturePrevious);
        int upload = initializeBody.indexOf("GL15.glBufferData(TARGET, size, GL15.GL_DYNAMIC_DRAW);", bindGenerated);
        int bindBase = initializeBody.indexOf("GL30.glBindBufferBase(TARGET, binding, buffer);", upload);
        int restore = initializeBody.indexOf("restoreGenericBufferBinding(previousBuffer, setupFailure);", bindBase);

        assertTrue(generated >= 0);
        assertTrue(put > generated);
        assertTrue(capturePrevious > put);
        assertTrue(bindGenerated > capturePrevious);
        assertTrue(upload > bindGenerated);
        assertTrue(bindBase > upload);
        assertTrue(restore > bindBase);
        assertFalse(initializeBody.contains("GL15.glBindBuffer(TARGET, 0);"));
    }

    @Test
    public void bindAllRestoresPreviousGenericSsboBindingAfterIndexedBindings() throws IOException {
        String source = readManagerSource();
        String bindAllBody = methodBody(source, "public void bindAll()");

        int supportCheck = bindAllBody.indexOf("if (!storageBufferSupport.getAsBoolean())");
        int capturePrevious = bindAllBody.indexOf(
            "int previousBuffer = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER_BINDING);",
            supportCheck);
        int tryBlock = bindAllBody.indexOf("try {", capturePrevious);
        int bindBase = bindAllBody.indexOf("GL30.glBindBufferBase(TARGET, entry.getKey(), entry.getValue());",
            tryBlock);
        int finallyBlock = bindAllBody.indexOf("} finally {", bindBase);
        int restore = bindAllBody.indexOf("restoreGenericBufferBinding(previousBuffer, bindingFailure);", finallyBlock);

        assertTrue(supportCheck >= 0);
        assertTrue(capturePrevious > supportCheck);
        assertTrue(tryBlock > capturePrevious);
        assertTrue(bindBase > tryBlock);
        assertTrue(finallyBlock > bindBase);
        assertTrue(restore > finallyBlock);
    }

    @Test
    public void restoreFailuresPreserveOriginalSetupOrBindingFailure() throws IOException {
        String source = readManagerSource();
        String initializeBody = methodBody(source, "public void initialize()");
        String bindAllBody = methodBody(source, "public void bindAll()");
        String restoreBody = methodBody(source,
            "private static void restoreGenericBufferBinding(int previousBuffer, Throwable primaryFailure)");

        int initializeSetupFailure = initializeBody.indexOf("Throwable setupFailure = null;");
        int initializeCatch = initializeBody.indexOf("catch (RuntimeException | Error exception)",
            initializeSetupFailure);
        int initializeCapture = initializeBody.indexOf("setupFailure = exception;", initializeCatch);
        int initializeRethrow = initializeBody.indexOf("throw exception;", initializeCapture);
        int initializeRestore = initializeBody.indexOf("restoreGenericBufferBinding(previousBuffer, setupFailure);",
            initializeRethrow);

        int bindFailure = bindAllBody.indexOf("Throwable bindingFailure = null;");
        int bindCatch = bindAllBody.indexOf("catch (RuntimeException | Error exception)", bindFailure);
        int bindCapture = bindAllBody.indexOf("bindingFailure = exception;", bindCatch);
        int bindRethrow = bindAllBody.indexOf("throw exception;", bindCapture);
        int bindRestore = bindAllBody.indexOf("restoreGenericBufferBinding(previousBuffer, bindingFailure);",
            bindRethrow);

        assertTrue(initializeSetupFailure >= 0);
        assertTrue(initializeCatch > initializeSetupFailure);
        assertTrue(initializeCapture > initializeCatch);
        assertTrue(initializeRethrow > initializeCapture);
        assertTrue(initializeRestore > initializeRethrow);
        assertTrue(bindFailure >= 0);
        assertTrue(bindCatch > bindFailure);
        assertTrue(bindCapture > bindCatch);
        assertTrue(bindRethrow > bindCapture);
        assertTrue(bindRestore > bindRethrow);
        assertTrue(restoreBody.contains("if (primaryFailure != null)"));
        assertTrue(restoreBody.contains("suppressFailure(primaryFailure, restoreFailure);"));
        assertTrue(restoreBody.contains("throw restoreFailure;"));
    }

    @Test
    public void ssboSuppressionHelperIgnoresSameThrowableAndKeepsDistinctContext() throws Exception {
        RuntimeException primary = new RuntimeException("primary ssbo failure");
        RuntimeException restore = new RuntimeException("restore ssbo failure");

        suppressFailure(primary, primary);
        assertEquals(0, primary.getSuppressed().length);

        suppressFailure(primary, restore);
        assertEquals(1, primary.getSuppressed().length);
        assertSame(restore, primary.getSuppressed()[0]);
    }

    @Test
    public void destroyAlwaysClearsStateAndLogsDeleteFailures() throws IOException {
        String source = readManagerSource();
        String destroyBody = methodBody(source, "public void destroy()");
        String deleteBody = methodBody(source, "private static void deleteBuffer(int buffer)");

        int tryBlock = destroyBody.indexOf("try {");
        int deleteCall = destroyBody.indexOf("deleteBuffer(buffer);", tryBlock);
        int finallyBlock = destroyBody.indexOf("} finally {", deleteCall);
        int clear = destroyBody.indexOf("buffers.clear();", finallyBlock);
        int initialized = destroyBody.indexOf("initialized = false;", clear);

        assertTrue(tryBlock >= 0);
        assertTrue(deleteCall > tryBlock);
        assertTrue(finallyBlock > deleteCall);
        assertTrue(clear > finallyBlock);
        assertTrue(initialized > clear);
        assertTrue(deleteBody.contains("if (buffer <= 0)"));
        assertTrue(deleteBody.contains("GL15.glDeleteBuffers(buffer);"));
        assertTrue(deleteBody.contains("catch (RuntimeException | Error exception)"));
        assertTrue(deleteBody.contains("Failed to delete shader storage buffer"));
    }

    @Test
    public void emptyDeclarationsInitializeWithoutGlSupport() {
        ShaderStorageBufferManager manager = new ShaderStorageBufferManager(
            new ShaderProperties(""),
            () -> false);

        manager.initialize();
        assertTrue(manager.isInitialized());
    }

    private static String readManagerSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/buffer/ShaderStorageBufferManager.java")), StandardCharsets.UTF_8);
    }

    private static void suppressFailure(Throwable failure, Throwable exception) throws Exception {
        Method method = ShaderStorageBufferManager.class.getDeclaredMethod(
            "suppressFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, exception);
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

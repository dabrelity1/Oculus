package net.oculus.compat.relictium;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RelictiumTerrainBindingAugmentationSourceTest {
    @Test
    public void multidrawAugmentationSkipsInstancedModelOffsetBinding() throws Exception {
        assertSkipsInstancedBindings(
            "src/main/java/net/oculus/mixin/pipeline/MultidrawChunkRenderBackendMixin.java");
    }

    @Test
    public void oneshotAugmentationSkipsInstancedBindingsIfRelictiumAddsThem() throws Exception {
        assertSkipsInstancedBindings(
            "src/main/java/net/oculus/mixin/pipeline/ChunkOneshotGraphicsStateMixin.java");
    }

    private static void assertSkipsInstancedBindings(String path) throws Exception {
        String source = read(path);
        String body = methodBody(source,
            "private static TessellationBinding[] oculus$augmentBindings(GlVertexFormat<ChunkMeshAttribute> format, TessellationBinding[] bindings)");

        int binding = body.indexOf("TessellationBinding binding = bindings[i];");
        int guard = body.indexOf("if (binding.isInstanced())", binding);
        int preserve = body.indexOf("augmented[i] = binding;", guard);
        int continueIndex = body.indexOf("continue;", preserve);
        int augment = body.indexOf("OculusVertexBindingHelper.createAugmentedBindings", continueIndex);

        assertTrue("Augmentation must inspect each tessellation binding", binding >= 0);
        assertTrue("Instanced Relictium bindings must be detected before adding vertex attributes", guard > binding);
        assertTrue("Instanced Relictium bindings must be preserved unchanged", preserve > guard);
        assertTrue("Instanced Relictium bindings must skip the vertex-attribute augmentation path",
            continueIndex > preserve && augment > continueIndex);
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

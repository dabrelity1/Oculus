package net.oculus.texture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class Texture2DBindCacheSourceTest {
    @Test
    public void agent07TextureCodeDoesNotRestoreTexture2DBindingsWithRawGlBindTexture() throws IOException {
        List<String> offenders = new ArrayList<>();

        assertNoRawTexture2DBinds(Paths.get("src/main/java/net/oculus/texture"), offenders);
        assertNoRawTexture2DBinds(Paths.get("src/main/java/net/oculus/pipeline/texture"), offenders);

        assertTrue(offenders.toString(), offenders.isEmpty());
    }

    @Test
    public void customImageManagerRoutesVariableTexture2DBindsThroughGlStateManager() throws IOException {
        String source = read(Paths.get("src/main/java/net/oculus/pipeline/texture/CustomImageManager.java"));

        assertTrue(source.contains("if (target == GL11.GL_TEXTURE_2D)"));
        assertTrue(source.contains("GlStateManager.bindTexture(textureId);"));
        assertTrue(source.contains("return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);"));
        assertTrue(source.contains("return GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);"));
    }

    private static void assertNoRawTexture2DBinds(Path root, List<String> offenders) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    String source = read(path);
                    if (source.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D")) {
                        offenders.add(path.toString());
                    }
                });
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read " + path, exception);
        }
    }
}

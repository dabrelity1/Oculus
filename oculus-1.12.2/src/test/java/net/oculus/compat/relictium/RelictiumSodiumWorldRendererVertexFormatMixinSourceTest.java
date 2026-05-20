package net.oculus.compat.relictium;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RelictiumSodiumWorldRendererVertexFormatMixinSourceTest {
    @Test
    public void activeMixinConfigQueuesExtendedTerrainVertexFormatRedirect() throws Exception {
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(config.contains("pipeline.RelictiumSodiumWorldRendererVertexFormatMixin"));
    }

    @Test
    public void vertexFormatRedirectPreservesRelictiumOriginalWhenShadersAreDisabled() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumSodiumWorldRendererVertexFormatMixin.java");

        assertTrue(source.contains("createChunkRenderBackend("));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()"));
        assertTrue(source.contains("? OculusTerrainVertexType.INSTANCE"));
        assertTrue(source.contains(": vertexFormat"));
        assertTrue(source.contains("OculusRuntimeValidation.logRelictiumTerrainVertexFormat("));
        assertTrue(source.contains("resolvedVertexType == OculusTerrainVertexType.INSTANCE"));
        assertTrue(source.contains("return createChunkRenderBackend(device, options, resolvedVertexType);"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

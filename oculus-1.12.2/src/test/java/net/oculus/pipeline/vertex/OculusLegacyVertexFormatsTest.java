package net.oculus.pipeline.vertex;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusLegacyVertexFormatsTest {
    @Test
    public void terrainGenericAttributesMatchLegacyShaderContract() throws Exception {
        String source = read();

        assertTrue(source.contains(
            "new VertexFormatElement(11, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.GENERIC, 2);"));
        assertTrue(source.contains(
            "new VertexFormatElement(12, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.GENERIC, 2);"));
        assertTrue(source.contains(
            "new VertexFormatElement(13, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.GENERIC, 4);"));
        assertTrue(source.contains(
            "new VertexFormatElement(14, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.GENERIC, 4);"));
    }

    @Test
    public void terrainLayoutOffsetsMatchBufferBuilderPlaceholderWrites() throws Exception {
        String source = read();

        assertTrue(source.contains("TERRAIN(OculusLegacyVertexFormats.TERRAIN, DefaultVertexFormats.BLOCK, 52, 28,"));
        assertTrue(source.contains("0, 16, 20, 28, 32, 36, 40, 44, 48, true)"));
    }

    @Test
    public void vanillaAndExtendedTerrainFormatsResolveToSameLayout() throws Exception {
        String source = read();

        assertTrue(source.contains("if (format == DefaultVertexFormats.BLOCK || format == TERRAIN)"));
        assertTrue(source.contains("return Layout.TERRAIN;"));
    }

    private static String read() throws Exception {
        return new String(Files.readAllBytes(
            Paths.get("src/main/java/net/oculus/pipeline/vertex/OculusLegacyVertexFormats.java")),
            StandardCharsets.UTF_8);
    }
}

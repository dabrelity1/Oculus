package net.oculus.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusTerrainVertexTypeSourceTest {
    @Test
    public void materialAttributeUsesSignedShortLikeReference() throws Exception {
        String activeFormat = read("src/main/java/net/oculus/pipeline/OculusTerrainVertexType.java");
        String activeFormats = read("src/main/java/net/oculus/pipeline/vertex/OculusGlVertexAttributeFormats.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/compat/sodium/impl/vertex_format/terrain_xhfp/XHFPModelVertexType.java");

        assertTrue(reference.contains(
            ".addElement(IrisChunkMeshAttributes.BLOCK_ID, 36, IrisGlVertexAttributeFormat.SHORT, 2, false)"));
        assertTrue(activeFormats.contains(
            "public static final GlVertexAttributeFormat SHORT = create(GL11.GL_SHORT, 2);"));
        assertTrue(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.MATERIAL, 32, OculusGlVertexAttributeFormats.SHORT, 2, false)"));
        assertFalse(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.MATERIAL, 32, GlVertexAttributeFormat.UNSIGNED_SHORT"));
    }

    @Test
    public void terrainWriterOffsetsMatchDeclaredRelictiumVertexFormat() throws Exception {
        String activeFormat = read("src/main/java/net/oculus/pipeline/OculusTerrainVertexType.java");
        String writer = read("src/main/java/net/oculus/pipeline/OculusTerrainVertexBufferWriterNio.java");

        assertTrue(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.NORMAL, 28, OculusGlVertexAttributeFormats.BYTE, 4, true)"));
        assertTrue(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.MATERIAL, 32, OculusGlVertexAttributeFormats.SHORT, 2, false)"));
        assertTrue(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.MID_UV, 36, GlVertexAttributeFormat.FLOAT, 2, false)"));
        assertTrue(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.TANGENT, 44, OculusGlVertexAttributeFormats.BYTE, 4, true)"));
        assertTrue(activeFormat.contains(
            ".addElement(OculusChunkMeshAttributes.MID_BLOCK, 48, OculusGlVertexAttributeFormats.BYTE, 4, false)"));

        assertTrue(writer.contains("private static final int OFFSET_NORMAL_X = 28;"));
        assertTrue(writer.contains("private static final int OFFSET_MATERIAL_ID = 32;"));
        assertTrue(writer.contains("private static final int OFFSET_RENDER_TYPE = 34;"));
        assertTrue(writer.contains("private static final int OFFSET_MID_U = 36;"));
        assertTrue(writer.contains("private static final int OFFSET_MID_V = 40;"));
        assertTrue(writer.contains("private static final int OFFSET_TANGENT = 44;"));
        assertTrue(writer.contains("private static final int OFFSET_MID_BLOCK = 48;"));
        assertTrue(writer.contains("private static final int OFFSET_BLOCK_EMISSION = 51;"));
        assertTrue(writer.contains("buffer.putShort(offset + OFFSET_MATERIAL_ID, materialId);"));
        assertTrue(writer.contains("buffer.putShort(offset + OFFSET_RENDER_TYPE, renderType);"));
        assertTrue(writer.contains("buffer.put(offset + OFFSET_BLOCK_EMISSION, blockEmission);"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

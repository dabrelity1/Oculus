package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.type.BlittableVertexType;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import net.minecraft.client.renderer.BufferBuilder;
import net.oculus.pipeline.vertex.OculusChunkMeshAttributes;
import net.oculus.pipeline.vertex.OculusGlVertexAttributeFormats;

/**
 * Legacy port of the Sodium/Iris terrain vertex type tailored for the Oculus pipeline.
 * Provides the layout and factory methods needed to encode chunk mesh data into the
 * extended vertex format used by the shader pipeline.
 */
public final class OculusTerrainVertexType implements ChunkVertexType {
    public static final OculusTerrainVertexType INSTANCE = new OculusTerrainVertexType();

    public static final int STRIDE = 52;

    // Mirrors the 52-byte layout written by OculusTerrainVertexBufferWriterNio.
    private static final GlVertexFormat<ChunkMeshAttribute> FORMAT = GlVertexFormat
        .builder(ChunkMeshAttribute.class, STRIDE)
        .addElement(ChunkMeshAttribute.POSITION, 0, GlVertexAttributeFormat.FLOAT, 3, false)
        .addElement(ChunkMeshAttribute.COLOR, 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true)
        .addElement(ChunkMeshAttribute.TEXTURE, 16, GlVertexAttributeFormat.FLOAT, 2, false)
        .addElement(ChunkMeshAttribute.LIGHT, 24, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false)
        .addElement(OculusChunkMeshAttributes.NORMAL, 28, OculusGlVertexAttributeFormats.BYTE, 4, true)
        .addElement(OculusChunkMeshAttributes.MATERIAL, 32, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false)
        .addElement(OculusChunkMeshAttributes.MID_UV, 36, GlVertexAttributeFormat.FLOAT, 2, false)
        .addElement(OculusChunkMeshAttributes.TANGENT, 44, OculusGlVertexAttributeFormats.BYTE, 4, true)
        .addElement(OculusChunkMeshAttributes.MID_BLOCK, 48, GlVertexAttributeFormat.UNSIGNED_BYTE, 3, false)
        .build();

    private static final float MODEL_SCALE = 1.0f;
    private static final float TEXTURE_SCALE = 1.0f;

    private OculusTerrainVertexType() {
    }

    @Override
    public ModelVertexSink createFallbackWriter(BufferBuilder consumer) {
        throw new UnsupportedOperationException("Fallback BufferBuilder path is not implemented yet");
    }

    @Override
    public ModelVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return new OculusTerrainVertexBufferWriterNio(buffer);
    }

    @Override
    public BlittableVertexType<ModelVertexSink> asBlittable() {
        return this;
    }

    @Override
    public GlVertexFormat<ChunkMeshAttribute> getCustomVertexFormat() {
        return FORMAT;
    }

    @Override
    public float getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
    }
}

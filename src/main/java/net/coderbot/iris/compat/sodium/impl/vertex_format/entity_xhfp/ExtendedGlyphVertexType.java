package net.coderbot.iris.compat.sodium.impl.vertex_format.entity_xhfp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.embeddedt.embeddium.client.model.vertex.buffer.VertexBufferView;
import org.embeddedt.embeddium.client.model.vertex.formats.glyph.GlyphVertexSink;
import org.embeddedt.embeddium.client.model.vertex.formats.glyph.writer.GlyphVertexWriterFallback;
import org.embeddedt.embeddium.client.model.vertex.type.BlittableVertexType;
import org.embeddedt.embeddium.client.model.vertex.type.VanillaVertexType;
import net.coderbot.iris.vertices.IrisVertexFormats;

public class ExtendedGlyphVertexType implements VanillaVertexType<GlyphVertexSink>, BlittableVertexType<GlyphVertexSink> {
	public static final ExtendedGlyphVertexType INSTANCE = new ExtendedGlyphVertexType();

	@Override
	public GlyphVertexSink createFallbackWriter(VertexConsumer vertexConsumer) {
		return new GlyphVertexWriterFallback(vertexConsumer);
	}

	@Override
	public GlyphVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
		return direct ? new GlyphVertexBufferWriterUnsafe(buffer) : new GlyphVertexBufferWriterNio(buffer);
	}

	@Override
	public VertexFormat getVertexFormat() {
		return IrisVertexFormats.TERRAIN;
	}

	public BlittableVertexType<GlyphVertexSink> asBlittable() {
		return this;
	}
}

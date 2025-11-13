package net.coderbot.iris.compat.sodium.mixin.vertex_format;

import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import org.embeddedt.embeddium.client.gl.attribute.GlVertexAttributeBinding;
import org.embeddedt.embeddium.client.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.client.gl.buffer.VertexData;
import org.embeddedt.embeddium.client.render.chunk.backends.oneshot.ChunkOneshotGraphicsState;
import org.embeddedt.embeddium.client.render.chunk.format.ChunkMeshAttribute;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.sodium.impl.IrisChunkShaderBindingPoints;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisChunkMeshAttributes;

@Mixin(ChunkOneshotGraphicsState.class)
public abstract class MixinChunkOneshotGraphicsState {
	@Unique
	private GlVertexFormat<ChunkMeshAttribute> vertexFormat;

	@ModifyArg(method = "upload", remap = false,
			at = @At(value = "INVOKE",
					target = "org/embeddedt/embeddium/client/gl/device/CommandList.uploadData (" +
							"Lorg/embeddedt/embeddium/client/gl/buffer/GlMutableBuffer;" +
							"Lorg/embeddedt/embeddium/client/gl/buffer/VertexData;" +
						")V",
					remap = false))
	@SuppressWarnings("unchecked")
	private VertexData iris$captureVertexFormat(VertexData vertexData) {
		vertexFormat = (GlVertexFormat<ChunkMeshAttribute>) vertexData.format;

		return vertexData;
	}

	@ModifyArg(method = "upload", remap = false,
			at = @At(value = "INVOKE",
					target = "org/embeddedt/embeddium/client/gl/tessellation/TessellationBinding.<init> (" +
							"Lorg/embeddedt/embeddium/client/gl/buffer/GlBuffer;" +
							"[Lorg/embeddedt/embeddium/client/gl/attribute/GlVertexAttributeBinding;" +
							"Z" +
						")V",
					remap = false,
					ordinal = 0))
	private GlVertexAttributeBinding[] iris$addAdditionalBindings(GlVertexAttributeBinding[] base) {
		return BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat() ? ArrayUtils.addAll(base,
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.BLOCK_ID,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.BLOCK_ID)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.MID_TEX_COORD,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.MID_TEX_COORD)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.TANGENT,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.TANGENT)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.NORMAL,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.NORMAL))
		) : base;
	}
}

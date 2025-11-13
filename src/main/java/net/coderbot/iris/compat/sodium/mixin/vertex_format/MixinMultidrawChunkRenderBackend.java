package net.coderbot.iris.compat.sodium.mixin.vertex_format;

import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import org.embeddedt.embeddium.client.gl.attribute.GlVertexAttributeBinding;
import org.embeddedt.embeddium.client.model.vertex.type.ChunkVertexType;
import org.embeddedt.embeddium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import org.embeddedt.embeddium.client.render.chunk.backends.multidraw.MultidrawGraphicsState;
import org.embeddedt.embeddium.client.render.chunk.shader.ChunkRenderShaderBackend;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.sodium.impl.IrisChunkShaderBindingPoints;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisChunkMeshAttributes;

@Mixin(MultidrawChunkRenderBackend.class)
public abstract class MixinMultidrawChunkRenderBackend extends ChunkRenderShaderBackend<MultidrawGraphicsState> {
	public MixinMultidrawChunkRenderBackend(ChunkVertexType vertexType) {
		// make compiler happy
		super(vertexType);
	}

	@ModifyArg(method = "createRegionTessellation", remap = false,
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
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.NORMAL,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.NORMAL)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.TANGENT,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.TANGENT)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.MID_TEX_COORD,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.MID_TEX_COORD)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.BLOCK_ID,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.BLOCK_ID)),
				new GlVertexAttributeBinding(IrisChunkShaderBindingPoints.MID_BLOCK,
						vertexFormat.getAttribute(IrisChunkMeshAttributes.MID_BLOCK))
		) : base;
	}
}

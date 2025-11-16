package net.coderbot.iris.mixin.vertices;

import java.nio.ByteBuffer;

import net.coderbot.iris.vertices.*;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.vendored.joml.Vector3f;

/**
 * Dynamically and transparently extends the vanilla vertex formats with additional data
 */
@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder implements BufferVertexConsumer, BlockSensitiveBufferBuilder, ExtendingBufferBuilder {
	@Unique
	private boolean extending;

	@Unique
	private boolean iris$shouldNotExtend = false;

	@Unique
	private boolean iris$isTerrain = false;

	@Unique
	private int vertexCount;

	@Unique
	private final BufferBuilderPolygonView polygon = new BufferBuilderPolygonView();

	@Unique
	private final Vector3f normal = new Vector3f();

	@Unique
	private boolean injectNormal;

	@Unique
	private short currentBlock;

	@Unique
	private short currentRenderType;

	@Unique
	private int currentLocalPosX;

	@Unique
	private int currentLocalPosY;

	@Unique
	private int currentLocalPosZ;

	@Shadow
	private boolean fastFormat;

	@Shadow
	private boolean fullFormat;

	@Shadow
	private ByteBuffer buffer;

	@Shadow
	private int mode;

	@Shadow
	private VertexFormat format;

	@Shadow
	private int nextElementByte;

	@Shadow
	private @Nullable VertexFormatElement currentElement;

	@Shadow
	public abstract void begin(int drawMode, VertexFormat vertexFormat);

	@Shadow
	public abstract void putShort(int i, short s);

	@Shadow
	protected abstract void switchFormat(VertexFormat format);
	
	@Shadow
	protected abstract void ensureCapacity(int i);

	@Override
	public void iris$beginWithoutExtending(int drawMode, VertexFormat vertexFormat) {
		iris$shouldNotExtend = true;
		begin(drawMode, vertexFormat);
		iris$shouldNotExtend = false;
	}

	@Inject(method = "begin", at = @At("HEAD"))
	private void iris$onBegin(int drawMode, VertexFormat format, CallbackInfo ci) {
		boolean shouldExtend = (!iris$shouldNotExtend) && BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat();
		extending = shouldExtend && (format == DefaultVertexFormat.BLOCK || format == DefaultVertexFormat.NEW_ENTITY
			|| format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
		vertexCount = 0;

		if (extending) {
			injectNormal = format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;
		}
	}

	@Inject(method = "begin", at = @At("RETURN"))
	private void iris$afterBegin(int drawMode, VertexFormat format, CallbackInfo ci) {
		if (extending) {
			if (format == DefaultVertexFormat.NEW_ENTITY) {
				this.switchFormat(IrisVertexFormats.ENTITY);
				this.iris$isTerrain = false;
			} else {
				this.switchFormat(IrisVertexFormats.TERRAIN);
				this.iris$isTerrain = true;
			}
			this.currentElement = this.format.getElements().get(0);
		}
	}

	@Inject(method = "discard", at = @At("HEAD"))
	private void iris$onReset(CallbackInfo ci) {
		extending = false;
		vertexCount = 0;
	}

	@Inject(method = "switchFormat", at = @At("RETURN"))
	private void iris$preventHardcodedVertexWriting(VertexFormat format, CallbackInfo ci) {
		if (!extending) {
			return;
		}

		fastFormat = false;
		fullFormat = false;
	}

	@Inject(method = "endVertex", at = @At("HEAD"))
	private void iris$beforeNext(CallbackInfo ci) {
		if (!extending) {
			return;
		}

		if (injectNormal && currentElement == DefaultVertexFormat.ELEMENT_NORMAL) {
			this.putInt(0, 0);
			this.nextElement();
		}
		
		// For Create Contraptions
		this.ensureCapacity(15);

		if (iris$isTerrain) {
			// ENTITY_ELEMENT
			this.putShort(0, currentBlock);
			this.putShort(2, currentRenderType);
			this.nextElement();
		}
		// MID_TEXTURE_ELEMENT
		this.putFloat(0, 0);
		this.putFloat(4, 0);
		this.nextElement();
		// TANGENT_ELEMENT
		this.putInt(0, 0);
		this.nextElement();
		if (iris$isTerrain) {
			// MID_BLOCK_ELEMENT
			int posIndex = this.nextElementByte - 48;
			float x = buffer.getFloat(posIndex);
			float y = buffer.getFloat(posIndex + 4);
			float z = buffer.getFloat(posIndex + 8);
			this.putInt(0, ExtendedDataHelper.computeMidBlock(x, y, z, currentLocalPosX, currentLocalPosY, currentLocalPosZ));
			this.nextElement();
		}

		vertexCount++;

		if (mode == GL11.GL_QUADS && vertexCount == 4 || mode == GL11.GL_TRIANGLES && vertexCount == 3) {
			fillExtendedData(vertexCount);
		}
	}

	@Unique
	private void fillExtendedData(int vertexAmount) {
		vertexCount = 0;

		int stride = format.getVertexSize();

		polygon.setup(buffer, nextElementByte, stride, vertexAmount);

		float midU = 0;
		float midV = 0;

		for (int vertex = 0; vertex < vertexAmount; vertex++) {
			midU += polygon.u(vertex);
			midV += polygon.v(vertex);
		}

		midU /= vertexAmount;
		midV /= vertexAmount;

		int midUOffset;
		int midVOffset;
		int normalOffset;
		int tangentOffset;
		if (iris$isTerrain) {
			midUOffset = 16;
			midVOffset = 12;
			normalOffset = 24;
			tangentOffset = 8;
		} else {
			midUOffset = 12;
			midVOffset = 8;
			normalOffset = 16;
			tangentOffset = 4;
		}

		if (vertexAmount == 3) {
			// NormalHelper.computeFaceNormalTri(normal, polygon);	// Removed to enable smooth shaded triangles. Mods rendering triangles with bad normals need to recalculate their normals manually or otherwise shading might be inconsistent.

			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				int packedNormal = buffer.getInt(nextElementByte - normalOffset - stride * vertex); // retrieve per-vertex normal

				int tangent = NormalHelper.computeTangentSmooth(NormI8.unpackX(packedNormal), NormI8.unpackY(packedNormal), NormI8.unpackZ(packedNormal), polygon);

				buffer.putFloat(nextElementByte - midUOffset - stride * vertex, midU);
				buffer.putFloat(nextElementByte - midVOffset - stride * vertex, midV);
				buffer.putInt(nextElementByte - tangentOffset - stride * vertex, tangent);
			}
		} else {
			NormalHelper.computeFaceNormal(normal, polygon);
			int packedNormal = NormI8.pack(normal.x, normal.y, normal.z, 0.0f);
			int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, polygon);

			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				buffer.putFloat(nextElementByte - midUOffset - stride * vertex, midU);
				buffer.putFloat(nextElementByte - midVOffset - stride * vertex, midV);
				buffer.putInt(nextElementByte - normalOffset - stride * vertex, packedNormal);
				buffer.putInt(nextElementByte - tangentOffset - stride * vertex, tangent);
			}
		}
	}

	@Override
	public void beginBlock(short block, short renderType, int localPosX, int localPosY, int localPosZ) {
		this.currentBlock = block;
		this.currentRenderType = renderType;
		this.currentLocalPosX = localPosX;
		this.currentLocalPosY = localPosY;
		this.currentLocalPosZ = localPosZ;
	}

	@Override
	public void endBlock() {
		this.currentBlock = -1;
		this.currentRenderType = -1;
		this.currentLocalPosX = 0;
		this.currentLocalPosY = 0;
		this.currentLocalPosZ = 0;
	}

	@Unique
	private void putInt(int i, int value) {
		this.buffer.putInt(this.nextElementByte + i, value);
	}
}

package net.oculus.mixin.vertexformat;

import java.nio.ByteOrder;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.oculus.pipeline.BlockContextHolder;
import net.oculus.pipeline.BlockRenderingSettings;
import net.oculus.pipeline.math.Vector3f;
import net.oculus.pipeline.vertex.OculusBlockSensitiveBufferBuilder;
import net.oculus.pipeline.vertex.OculusBufferBuilderPolygonView;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import net.oculus.pipeline.vertex.FluidSeparateAoTracker;
import net.oculus.pipeline.vertex.OculusLegacyVertexFormats;
import net.oculus.pipeline.vertex.OculusNormalHelper;
import net.oculus.pipeline.vertex.SeparateAoTracker;
import net.oculus.pipeline.vertex.OculusLegacyVertexFormats.Layout;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderExtendedVertexFormatMixin implements OculusBlockSensitiveBufferBuilder {
    @Unique
    private static final int OCULUS_TERRAIN_COLOR_OFFSET = 12;

    @Shadow
    private ByteBuffer byteBuffer;

    @Shadow
    private IntBuffer rawIntBuffer;

    @Shadow
    private int vertexCount;

    @Shadow
    private boolean noColor;

    @Shadow
    private VertexFormatElement vertexFormatElement;

    @Shadow
    private int vertexFormatIndex;

    @Shadow
    private int drawMode;

    @Shadow
    private VertexFormat vertexFormat;

    @Shadow
    private void growBuffer(int size) {
    }

    @Shadow
    public abstract int getColorIndex(int vertexIndex);

    @Unique
    private Layout oculus$pendingLayout;

    @Unique
    private Layout oculus$layout;

    @Unique
    private boolean oculus$extending;

    @Unique
    private int oculus$directVerticesInPrimitive;

    @Unique
    private int oculus$bulkStartVertex = -1;

    @Unique
    private int oculus$bulkVertexCount;

    @Unique
    private BlockContextHolder oculus$contextHolder = BlockContextHolder.createActiveHolder();

    @Unique
    private short[] oculus$blockStack = new short[8];

    @Unique
    private short[] oculus$renderTypeStack = new short[8];

    @Unique
    private byte[] oculus$blockEmissionStack = new byte[8];

    @Unique
    private int[] oculus$localXStack = new int[8];

    @Unique
    private int[] oculus$localYStack = new int[8];

    @Unique
    private int[] oculus$localZStack = new int[8];

    @Unique
    private int oculus$contextStackSize;

    @Unique
    private final OculusBufferBuilderPolygonView oculus$polygon = new OculusBufferBuilderPolygonView();

    @Unique
    private final Vector3f oculus$normal = new Vector3f();

    @Inject(method = "begin", at = @At("HEAD"))
    private void oculus$beforeBegin(int glMode, VertexFormat format, CallbackInfo ci) {
        this.oculus$pendingLayout = null;
        this.oculus$layout = null;
        this.oculus$extending = false;
        this.oculus$directVerticesInPrimitive = 0;
        this.oculus$clearBlockContext();
        SeparateAoTracker.clear();

        if (BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) {
            this.oculus$pendingLayout = OculusLegacyVertexFormats.getExtendedLayout(format);
        }
    }

    @Inject(method = "begin", at = @At("RETURN"))
    private void oculus$afterBegin(int glMode, VertexFormat format, CallbackInfo ci) {
        Layout layout = this.oculus$pendingLayout;
        this.oculus$pendingLayout = null;

        if (layout == null) {
            return;
        }

        this.vertexFormat = layout.format;
        this.vertexFormatIndex = 0;
        this.vertexFormatElement = this.vertexFormat.getElement(0);
        this.oculus$layout = layout;
        this.oculus$extending = true;
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void oculus$onReset(CallbackInfo ci) {
        this.oculus$layout = null;
        this.oculus$extending = false;
        this.oculus$directVerticesInPrimitive = 0;
        this.oculus$bulkStartVertex = -1;
        this.oculus$bulkVertexCount = 0;
        this.oculus$clearBlockContext();
        SeparateAoTracker.clear();
    }

    @Inject(method = "putColorMultiplier", at = @At("HEAD"), cancellable = true)
    private void oculus$putSeparateAoColor(float red, float green, float blue, int vertexIndex, CallbackInfo ci) {
        Layout layout = this.oculus$layout;
        if (!this.oculus$extending || layout != Layout.TERRAIN || !BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
            return;
        }

        float ao = SeparateAoTracker.consume(vertexIndex);
        if (Float.isNaN(ao)) {
            return;
        }

        float redWithoutAo = ao > 1.0E-6F ? red / ao : red;
        float greenWithoutAo = ao > 1.0E-6F ? green / ao : green;
        float blueWithoutAo = ao > 1.0E-6F ? blue / ao : blue;
        this.oculus$putColorMultiplierWithAlpha(redWithoutAo, greenWithoutAo, blueWithoutAo, ao, vertexIndex);
        ci.cancel();
    }

    @Inject(method = "addVertexData", at = @At("HEAD"), cancellable = true)
    private void oculus$beforeAddVertexData(int[] data, CallbackInfo ci) {
        this.oculus$bulkStartVertex = -1;
        this.oculus$bulkVertexCount = 0;

        Layout layout = this.oculus$layout;
        if (!this.oculus$extending || layout == null || data == null || data.length == 0) {
            return;
        }

        int extendedInts = layout.extendedInts();
        if (data.length % extendedInts == 0) {
            this.oculus$bulkStartVertex = this.vertexCount;
            this.oculus$bulkVertexCount = data.length / extendedInts;
            return;
        }

        int baseInts = layout.baseInts();
        if (data.length % baseInts == 0) {
            int vertices = data.length / baseInts;
            int startVertex = this.vertexCount;
            this.growBuffer(vertices * layout.stride + layout.stride);
            this.oculus$appendExpandedData(data, layout, vertices);
            this.vertexCount += vertices;
            this.oculus$fillCompletedPrimitives(startVertex, vertices, layout);
            ci.cancel();
        }
    }

    @Inject(method = "addVertexData", at = @At("RETURN"))
    private void oculus$afterAddVertexData(int[] data, CallbackInfo ci) {
        Layout layout = this.oculus$layout;
        if (!this.oculus$extending || layout == null || this.oculus$bulkStartVertex < 0 || this.oculus$bulkVertexCount <= 0) {
            return;
        }

        this.oculus$fillCompletedPrimitives(this.oculus$bulkStartVertex, this.oculus$bulkVertexCount, layout);
        this.oculus$bulkStartVertex = -1;
        this.oculus$bulkVertexCount = 0;
    }

    @Inject(method = "endVertex", at = @At("HEAD"))
    private void oculus$beforeEndVertex(CallbackInfo ci) {
        Layout layout = this.oculus$layout;
        if (!this.oculus$extending || layout == null) {
            return;
        }

        this.growBuffer(layout.stride);
        this.oculus$writePlaceholders(this.vertexCount, layout);
    }

    @Inject(method = "endVertex", at = @At("RETURN"))
    private void oculus$afterEndVertex(CallbackInfo ci) {
        Layout layout = this.oculus$layout;
        if (!this.oculus$extending || layout == null) {
            return;
        }

        this.oculus$directVerticesInPrimitive++;
        int primitiveSize = this.oculus$primitiveSize();
        if (this.oculus$directVerticesInPrimitive == primitiveSize) {
            this.oculus$fillPrimitive(this.vertexCount - primitiveSize, primitiveSize, layout);
            this.oculus$directVerticesInPrimitive = 0;
        }

        this.vertexFormatIndex = 0;
        this.vertexFormatElement = this.vertexFormat.getElement(0);
    }

    @Override
    public void oculus$beginBlock(short block, short renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ) {
        if (this.oculus$contextHolder == null) {
            this.oculus$contextHolder = BlockContextHolder.createActiveHolder();
        }

        this.oculus$pushBlockContext();
        this.oculus$contextHolder.blockId = block;
        this.oculus$contextHolder.renderType = renderType;
        this.oculus$contextHolder.blockEmission = blockEmission;
        this.oculus$contextHolder.localPosX = localPosX;
        this.oculus$contextHolder.localPosY = localPosY;
        this.oculus$contextHolder.localPosZ = localPosZ;
    }

    @Override
    public void oculus$endBlock() {
        if (this.oculus$contextHolder != null) {
            this.oculus$popBlockContext();
        }
    }

    @Unique
    private void oculus$clearBlockContext() {
        if (this.oculus$contextHolder == null) {
            this.oculus$contextHolder = BlockContextHolder.createActiveHolder();
        } else {
            this.oculus$contextHolder.reset();
        }

        this.oculus$contextStackSize = 0;
    }

    @Unique
    private void oculus$pushBlockContext() {
        int size = this.oculus$contextStackSize;

        if (size == this.oculus$blockStack.length) {
            int newLength = size * 2;
            this.oculus$blockStack = java.util.Arrays.copyOf(this.oculus$blockStack, newLength);
            this.oculus$renderTypeStack = java.util.Arrays.copyOf(this.oculus$renderTypeStack, newLength);
            this.oculus$blockEmissionStack = java.util.Arrays.copyOf(this.oculus$blockEmissionStack, newLength);
            this.oculus$localXStack = java.util.Arrays.copyOf(this.oculus$localXStack, newLength);
            this.oculus$localYStack = java.util.Arrays.copyOf(this.oculus$localYStack, newLength);
            this.oculus$localZStack = java.util.Arrays.copyOf(this.oculus$localZStack, newLength);
        }

        this.oculus$blockStack[size] = this.oculus$contextHolder.blockId;
        this.oculus$renderTypeStack[size] = this.oculus$contextHolder.renderType;
        this.oculus$blockEmissionStack[size] = this.oculus$contextHolder.blockEmission;
        this.oculus$localXStack[size] = this.oculus$contextHolder.localPosX;
        this.oculus$localYStack[size] = this.oculus$contextHolder.localPosY;
        this.oculus$localZStack[size] = this.oculus$contextHolder.localPosZ;
        this.oculus$contextStackSize = size + 1;
    }

    @Unique
    private void oculus$popBlockContext() {
        int size = this.oculus$contextStackSize - 1;

        if (size < 0) {
            this.oculus$contextHolder.reset();
            return;
        }

        this.oculus$contextHolder.blockId = this.oculus$blockStack[size];
        this.oculus$contextHolder.renderType = this.oculus$renderTypeStack[size];
        this.oculus$contextHolder.blockEmission = this.oculus$blockEmissionStack[size];
        this.oculus$contextHolder.localPosX = this.oculus$localXStack[size];
        this.oculus$contextHolder.localPosY = this.oculus$localYStack[size];
        this.oculus$contextHolder.localPosZ = this.oculus$localZStack[size];
        this.oculus$contextStackSize = size;
    }

    @Unique
    private void oculus$appendExpandedData(int[] data, Layout layout, int vertices) {
        int baseInts = layout.baseInts();

        for (int vertex = 0; vertex < vertices; vertex++) {
            int source = vertex * baseInts;
            int target = (this.vertexCount + vertex) * layout.stride;

            if (layout == Layout.TERRAIN) {
                this.oculus$copyInt(data, source, target, 0, 0, 7);
            } else if (layout == Layout.ITEM) {
                this.oculus$copyInt(data, source, target, 0, 0, 7);
            } else if (layout == Layout.OLD_MODEL) {
                this.oculus$copyInt(data, source, target, 0, 0, 6);
            } else if (layout == Layout.POSITION_TEX_LMAP_COLOR) {
                this.oculus$copyInt(data, source, target, 0, 0, 7);
            } else if (layout == Layout.POSITION_TEX_COLOR_NORMAL) {
                this.oculus$copyInt(data, source, target, 0, 0, 7);
            }

            this.oculus$writePlaceholders(this.vertexCount + vertex, layout);
        }
    }

    @Unique
    private void oculus$copyInt(int[] data, int sourceVertex, int targetByte, int sourceIntOffset, int targetIntOffset, int count) {
        for (int i = 0; i < count; i++) {
            this.byteBuffer.putInt(targetByte + (targetIntOffset + i) * Integer.BYTES, data[sourceVertex + sourceIntOffset + i]);
        }
    }

    @Unique
    private void oculus$writePlaceholders(int vertex, Layout layout) {
        int base = vertex * layout.stride;

        if (layout.normalOffset >= 0) {
            this.byteBuffer.putInt(base + layout.normalOffset, 0);
        }

        if (layout.entityOffset >= 0) {
            BlockContextHolder context = this.oculus$contextHolder;
            short block = context != null ? context.blockId : -1;
            short renderType = context != null ? context.renderType : -1;
            this.byteBuffer.putShort(base + layout.entityOffset, block);
            this.byteBuffer.putShort(base + layout.entityOffset + 2, renderType);
        }

        this.byteBuffer.putFloat(base + layout.midUOffset, 0.0f);
        this.byteBuffer.putFloat(base + layout.midVOffset, 0.0f);
        this.byteBuffer.putInt(base + layout.tangentOffset, 0);

        if (layout.midBlockOffset >= 0) {
            BlockContextHolder context = this.oculus$contextHolder;
            int midBlock = 0;
            if (context != null) {
                float x = this.byteBuffer.getFloat(base + layout.positionOffset);
                float y = this.byteBuffer.getFloat(base + layout.positionOffset + 4);
                float z = this.byteBuffer.getFloat(base + layout.positionOffset + 8);
                midBlock = OculusExtendedDataHelper.computeMidBlock(x, y, z,
                    context.localPosX, context.localPosY, context.localPosZ);
            }

            this.byteBuffer.put(base + layout.midBlockOffset, (byte) (midBlock & 0xFF));
            this.byteBuffer.put(base + layout.midBlockOffset + 1, (byte) ((midBlock >> 8) & 0xFF));
            this.byteBuffer.put(base + layout.midBlockOffset + 2, (byte) ((midBlock >> 16) & 0xFF));
            this.byteBuffer.put(base + layout.midBlockOffset + 3, context != null ? context.blockEmission : 0);
        }
    }

    @Unique
    private void oculus$fillCompletedPrimitives(int startVertex, int vertexAmount, Layout layout) {
        int primitiveSize = this.oculus$primitiveSize();
        int end = startVertex + vertexAmount;

        for (int vertex = startVertex; vertex + primitiveSize <= end; vertex += primitiveSize) {
            this.oculus$fillPrimitive(vertex, primitiveSize, layout);
        }
    }

    @Unique
    private int oculus$primitiveSize() {
        return this.drawMode == GL11.GL_TRIANGLES ? 3 : 4;
    }

    @Unique
    private void oculus$putColorMultiplierWithAlpha(float red, float green, float blue, float alpha, int vertexIndex) {
        int colorIndex = this.getColorIndex(vertexIndex);
        int color = -1;

        if (!this.noColor) {
            color = this.rawIntBuffer.get(colorIndex);

            int redValue;
            int greenValue;
            int blueValue;
            int alphaValue = oculus$clampColor((int) (oculus$clampUnit(alpha) * 255.0F));

            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                redValue = oculus$clampColor((int) ((color & 255) * red));
                greenValue = oculus$clampColor((int) (((color >> 8) & 255) * green));
                blueValue = oculus$clampColor((int) (((color >> 16) & 255) * blue));
                color = (alphaValue << 24) | (blueValue << 16) | (greenValue << 8) | redValue;
            } else {
                redValue = oculus$clampColor((int) (((color >> 24) & 255) * red));
                greenValue = oculus$clampColor((int) (((color >> 16) & 255) * green));
                blueValue = oculus$clampColor((int) (((color >> 8) & 255) * blue));
                color = (redValue << 24) | (greenValue << 16) | (blueValue << 8) | alphaValue;
            }
        }

        this.rawIntBuffer.put(colorIndex, color);
    }

    @Unique
    private static int oculus$clampColor(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }

    @Unique
    private static float oculus$clampUnit(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    @Unique
    private void oculus$fillPrimitive(int startVertex, int vertexAmount, Layout layout) {
        if (vertexAmount < 3) {
            return;
        }

        this.oculus$polygon.setup(this.byteBuffer, startVertex, layout.stride,
            layout.positionOffset, layout.uOffset, layout.vOffset);

        float midU = 0.0f;
        float midV = 0.0f;
        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            midU += this.oculus$polygon.u(vertex);
            midV += this.oculus$polygon.v(vertex);
        }

        midU /= vertexAmount;
        midV /= vertexAmount;

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int base = (startVertex + vertex) * layout.stride;
            this.byteBuffer.putFloat(base + layout.midUOffset, midU);
            this.byteBuffer.putFloat(base + layout.midVOffset, midV);
        }

        if (vertexAmount == 4) {
            OculusNormalHelper.computeFaceNormal(this.oculus$normal, this.oculus$polygon);
        } else {
            this.oculus$computeTriangleNormal(this.oculus$normal);
        }

        int packedNormal = OculusNormalHelper.packNormal(this.oculus$normal, 0.0f);
        int tangent = OculusNormalHelper.computeTangent(this.oculus$normal.x, this.oculus$normal.y, this.oculus$normal.z,
            this.oculus$polygon);

        this.oculus$applyFluidSeparateAo(startVertex, vertexAmount, layout, this.oculus$normal);

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int base = (startVertex + vertex) * layout.stride;
            if (layout.normalOffset >= 0) {
                this.byteBuffer.putInt(base + layout.normalOffset, packedNormal);
            }
            this.byteBuffer.putInt(base + layout.tangentOffset, tangent);
        }
    }

    @Unique
    private void oculus$applyFluidSeparateAo(int startVertex, int vertexAmount, Layout layout, Vector3f normal) {
        if (!BlockRenderingSettings.INSTANCE.shouldUseSeparateAo() || this.noColor || layout != Layout.TERRAIN || vertexAmount != 4) {
            return;
        }

        BlockContextHolder context = this.oculus$contextHolder;
        if (context == null || context.renderType != OculusExtendedDataHelper.FLUID_RENDER_TYPE) {
            return;
        }

        boolean bottomFace = FluidSeparateAoTracker.isDownFacingNormal(normal.y)
            && this.oculus$isFluidBlockBottomFace(startVertex, vertexAmount, layout);
        float ao = FluidSeparateAoTracker.ambientOcclusionForFluidQuad(normal.x, normal.y, normal.z, bottomFace);
        if (Float.isNaN(ao)) {
            return;
        }

        int alphaValue = FluidSeparateAoTracker.alphaFromAo(ao);

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int colorIndex = ((startVertex + vertex) * layout.stride + OCULUS_TERRAIN_COLOR_OFFSET) / Integer.BYTES;
            int color = this.rawIntBuffer.get(colorIndex);
            int redValue;
            int greenValue;
            int blueValue;

            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                redValue = FluidSeparateAoTracker.colorComponentWithoutAo(color & 255, ao, 0, bottomFace);
                greenValue = FluidSeparateAoTracker.colorComponentWithoutAo((color >> 8) & 255, ao, 1, bottomFace);
                blueValue = FluidSeparateAoTracker.colorComponentWithoutAo((color >> 16) & 255, ao, 2, bottomFace);
                color = (alphaValue << 24) | (blueValue << 16) | (greenValue << 8) | redValue;
            } else {
                redValue = FluidSeparateAoTracker.colorComponentWithoutAo((color >> 24) & 255, ao, 0, bottomFace);
                greenValue = FluidSeparateAoTracker.colorComponentWithoutAo((color >> 16) & 255, ao, 1, bottomFace);
                blueValue = FluidSeparateAoTracker.colorComponentWithoutAo((color >> 8) & 255, ao, 2, bottomFace);
                color = (redValue << 24) | (greenValue << 16) | (blueValue << 8) | alphaValue;
            }

            this.rawIntBuffer.put(colorIndex, color);
        }
    }

    @Unique
    private boolean oculus$isFluidBlockBottomFace(int startVertex, int vertexAmount, Layout layout) {
        float firstY = this.byteBuffer.getFloat(startVertex * layout.stride + layout.positionOffset + Float.BYTES);

        for (int vertex = 1; vertex < vertexAmount; vertex++) {
            int base = (startVertex + vertex) * layout.stride;
            float y = this.byteBuffer.getFloat(base + layout.positionOffset + Float.BYTES);
            if (Math.abs(y - firstY) > 1.0E-4F) {
                return false;
            }
        }

        return Math.abs(firstY - Math.round(firstY)) < 1.0E-4F;
    }

    @Unique
    private void oculus$computeTriangleNormal(Vector3f out) {
        float x0 = this.oculus$polygon.x(0);
        float y0 = this.oculus$polygon.y(0);
        float z0 = this.oculus$polygon.z(0);
        float x1 = this.oculus$polygon.x(1);
        float y1 = this.oculus$polygon.y(1);
        float z1 = this.oculus$polygon.z(1);
        float x2 = this.oculus$polygon.x(2);
        float y2 = this.oculus$polygon.y(2);
        float z2 = this.oculus$polygon.z(2);

        float edge1x = x1 - x0;
        float edge1y = y1 - y0;
        float edge1z = z1 - z0;
        float edge2x = x2 - x0;
        float edge2y = y2 - y0;
        float edge2z = z2 - z0;

        float nx = edge1y * edge2z - edge1z * edge2y;
        float ny = edge1z * edge2x - edge1x * edge2z;
        float nz = edge1x * edge2y - edge1y * edge2x;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        if (length != 0.0f) {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        out.set(nx, ny, nz);
    }
}

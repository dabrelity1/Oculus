package net.oculus.mixins;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class BufferBuilderExtendedVertexFormatMixinSourceTest {
    @Test
    public void bufferBuilderMixinWritesSignedMaterialMidBlockAndEmissionBytes() throws Exception {
        String mixin = read("src/main/java/net/oculus/mixin/vertexformat/BufferBuilderExtendedVertexFormatMixin.java");
        String writeBody = methodBody(mixin, "private void oculus$writePlaceholders(int vertex, Layout layout)");
        String beginBody = methodBody(mixin,
            "public void oculus$beginBlock(short block, short renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ)");
        String popBody = methodBody(mixin, "private void oculus$popBlockContext()");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(config.contains("\"vertexformat.BufferBuilderExtendedVertexFormatMixin\""));
        assertTrue(writeBody.contains("short block = context != null ? context.blockId : -1;"));
        assertTrue(writeBody.contains("short renderType = context != null ? context.renderType : -1;"));
        assertTrue(writeBody.contains("this.byteBuffer.putShort(base + layout.entityOffset, block);"));
        assertTrue(writeBody.contains("this.byteBuffer.putShort(base + layout.entityOffset + 2, renderType);"));
        assertTrue(writeBody.contains("OculusExtendedDataHelper.computeMidBlock(x, y, z,"));
        assertTrue(writeBody.contains("this.byteBuffer.put(base + layout.midBlockOffset, (byte) (midBlock & 0xFF));"));
        assertTrue(writeBody.contains(
            "this.byteBuffer.put(base + layout.midBlockOffset + 3, context != null ? context.blockEmission : 0);"));
        assertTrue(beginBody.contains("this.oculus$pushBlockContext();"));
        assertTrue(beginBody.contains("this.oculus$contextHolder.blockEmission = blockEmission;"));
        assertTrue(popBody.contains("this.oculus$contextHolder.blockEmission = this.oculus$blockEmissionStack[size];"));
    }

    @Test
    public void fallbackWriterScopesBlockContextAroundEveryVertexWrite() throws Exception {
        String fallback = read("src/main/java/net/oculus/pipeline/OculusTerrainVertexWriterFallback.java");
        String writeBody = methodBody(fallback,
            "public void writeQuad(float x, float y, float z, int color, float u, float v, int light)");

        assertTrue(writeBody.contains("consumer instanceof OculusBlockSensitiveBufferBuilder"));
        assertTrue(writeBody.contains("((OculusBlockSensitiveBufferBuilder) consumer).oculus$beginBlock(context.blockId, context.renderType,"));
        assertTrue(writeBody.contains("context.blockEmission, context.localPosX, context.localPosY, context.localPosZ);"));
        assertTrue(writeBody.contains("try {"));
        assertTrue(writeBody.contains("consumer.endVertex();"));
        assertTrue(writeBody.contains("} finally {"));
        assertTrue(writeBody.contains("((OculusBlockSensitiveBufferBuilder) consumer).oculus$endBlock();"));
    }

    @Test
    public void terrainBlockContextsUseChunkLocalCoordinatesForMidBlock() throws Exception {
        String sodiumMixin = read("src/main/java/net/oculus/mixin/ChunkRenderRebuildTaskMixin.java");
        String modelMixin = read("src/main/java/net/oculus/mixin/vertexformat/BlockModelRendererBlockContextMixin.java");
        String fluidMixin = read("src/main/java/net/oculus/mixin/vertexformat/BlockFluidRendererBlockContextMixin.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/mixin/vertices/block_rendering/MixinChunkRebuildTask.java");

        assertTrue(reference.contains("pos.getX() & 0xF, pos.getY() & 0xF, pos.getZ() & 0xF"));
        assertTrue(sodiumMixin.contains("this.oculus_contextHolder.setLocalPos(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);"));
        assertTrue(modelMixin.contains("pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15"));
        assertTrue(fluidMixin.contains("pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15"));

        assertFalse(sodiumMixin.contains("setLocalPos(pos.getX(), pos.getY(), pos.getZ())"));
        assertFalse(modelMixin.contains("blockEmission, pos.getX(), pos.getY(), pos.getZ()"));
        assertFalse(fluidMixin.contains("blockEmission, pos.getX(), pos.getY(), pos.getZ()"));
    }

    @Test
    public void relictiumChunkBuildBuffersCreateContextBeforeTerrainWritersNeedIt() throws Exception {
        String buffersMixin = read("src/main/java/net/oculus/mixin/ChunkBuildBuffersMixin.java");
        String rebuildMixin = read("src/main/java/net/oculus/mixin/ChunkRenderRebuildTaskMixin.java");
        String getterBody = methodBody(buffersMixin, "public BlockContextHolder oculus_getContextHolder()");
        String initBody = methodBody(buffersMixin,
            "private void oculus$initContextHolder(ChunkRenderData.Builder renderData, CallbackInfo ci)");
        String helperBody = methodBody(buffersMixin, "private BlockContextHolder oculus$getOrCreateContextHolder()");
        String writerBody = methodBody(buffersMixin,
            "private VertexSink oculus$onCreateBufferWriter(ChunkVertexType type, VertexBufferView buffer, boolean direct)");

        assertTrue("Relictium build-task context lookup happens before ChunkBuildBuffers.init in installed bytecode",
            rebuildMixin.contains("this.oculus_contextHolder = ((IChunkBuildBuffers) buffers).oculus_getContextHolder();"));
        assertTrue("The context holder getter must be lazy so performBuild HEAD sees a usable holder",
            getterBody.contains("return this.oculus$getOrCreateContextHolder();"));
        assertTrue(buffersMixin.contains("@Inject(method = \"init\", at = @At(\"HEAD\"), remap = false)"));
        assertTrue(initBody.contains("this.oculus$getOrCreateContextHolder();"));
        assertTrue(helperBody.contains("if (this.oculus_contextHolder == null)"));
        assertTrue(helperBody.contains("this.oculus_contextHolder = BlockContextHolder.createActiveHolder();"));
        assertTrue(helperBody.contains("return this.oculus_contextHolder;"));
        assertTrue(writerBody.contains("((ContextAwareVertexWriter) sink).setContextHolder(this.oculus$getOrCreateContextHolder());"));
        assertFalse("Creating the holder only after init leaves Relictium writers without build-task context",
            buffersMixin.contains("@Inject(method = \"init\", at = @At(\"RETURN\"), remap = false)"));
    }

    @Test
    public void genericAttributeMixinEnablesLegacyGenericVertexAttributes() throws Exception {
        String attribMixin = read("src/main/java/net/oculus/mixin/vertexformat/ForgeHooksClientGenericAttribMixin.java");
        String genericElementMixin = read("src/main/java/net/oculus/mixin/vertexformat/VertexFormatElementGenericMixin.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(config.contains("\"vertexformat.ForgeHooksClientGenericAttribMixin\""));
        assertTrue(config.contains("\"vertexformat.VertexFormatElementGenericMixin\""));
        assertTrue(genericElementMixin.contains("if (usage == VertexFormatElement.EnumUsage.GENERIC)"));
        assertTrue(genericElementMixin.contains("cir.setReturnValue(true);"));
        assertTrue(attribMixin.contains("if (usage != VertexFormatElement.EnumUsage.GENERIC)"));
        assertTrue(attribMixin.contains("GL20.glEnableVertexAttribArray(index);"));
        assertTrue(attribMixin.contains("GL20.glVertexAttribPointer(index, attribute.getElementCount(),"));
        assertTrue(attribMixin.contains("boolean normalized = index == 13 && attribute.getType() == VertexFormatElement.EnumType.BYTE;"));
        assertTrue(attribMixin.contains("GL20.glDisableVertexAttribArray(format.getElement(element).getIndex());"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Unable to read method body for " + signature);
    }
}

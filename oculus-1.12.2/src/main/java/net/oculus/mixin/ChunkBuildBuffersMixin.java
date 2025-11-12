package net.oculus.mixin;

import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import net.oculus.mixin.extensions.IChunkBuildBuffers;
import net.oculus.pipeline.BlockContextHolder;
import net.oculus.pipeline.ContextAwareVertexWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkBuildBuffers.class, remap = false)
public abstract class ChunkBuildBuffersMixin implements IChunkBuildBuffers {
    @Unique
    private BlockContextHolder oculus_contextHolder;

    @Override
    public BlockContextHolder oculus_getContextHolder() {
        return this.oculus_contextHolder;
    }

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void oculus$initContextHolder(ChunkRenderData.Builder renderData, CallbackInfo ci) {
        if (this.oculus_contextHolder == null) {
            this.oculus_contextHolder = BlockContextHolder.createVanillaHolder();
        }
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "me/jellysquid/mods/sodium/client/model/vertex/type/ChunkVertexType.createBufferWriter(Lme/jellysquid/mods/sodium/client/model/vertex/buffer/VertexBufferView;Z)Lme/jellysquid/mods/sodium/client/model/vertex/VertexSink;"), remap = false)
    private VertexSink oculus$onCreateBufferWriter(ChunkVertexType type, VertexBufferView buffer, boolean direct) {
        VertexSink sink = type.createBufferWriter(buffer, direct);

        if (sink instanceof ContextAwareVertexWriter) {
            if (this.oculus_contextHolder == null) {
                this.oculus_contextHolder = BlockContextHolder.createVanillaHolder();
            }

            ((ContextAwareVertexWriter) sink).setContextHolder(this.oculus_contextHolder);
        }

        return sink;
    }
}

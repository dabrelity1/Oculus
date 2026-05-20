package net.oculus.mixin.pipeline;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.BlockRenderingSettings;
import net.oculus.pipeline.OculusTerrainVertexType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class RelictiumSodiumWorldRendererVertexFormatMixin {
    @Shadow
    private static ChunkRenderBackend<?> createChunkRenderBackend(RenderDevice device,
                                                                  SodiumGameOptions options,
                                                                  ChunkVertexType vertexFormat) {
        throw new AssertionError("Shadowed Relictium SodiumWorldRenderer.createChunkRenderBackend");
    }

    @Redirect(
        method = "initRenderer",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/SodiumWorldRenderer;createChunkRenderBackend(Lme/jellysquid/mods/sodium/client/gl/device/RenderDevice;Lme/jellysquid/mods/sodium/client/gui/SodiumGameOptions;Lme/jellysquid/mods/sodium/client/model/vertex/type/ChunkVertexType;)Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderBackend;"
        ),
        remap = false
    )
    private ChunkRenderBackend<?> oculus$createBackendWithExtendedTerrainVertexFormat(RenderDevice device,
                                                                                       SodiumGameOptions options,
                                                                                       ChunkVertexType vertexFormat) {
        boolean useExtendedVertexFormat = BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat();
        ChunkVertexType resolvedVertexType = useExtendedVertexFormat
            ? OculusTerrainVertexType.INSTANCE
            : vertexFormat;
        OculusRuntimeValidation.logRelictiumTerrainVertexFormat(
            describeVertexType(vertexFormat),
            describeVertexType(resolvedVertexType),
            useExtendedVertexFormat,
            resolvedVertexType == OculusTerrainVertexType.INSTANCE
        );
        return createChunkRenderBackend(device, options, resolvedVertexType);
    }

    private static String describeVertexType(ChunkVertexType vertexType) {
        return vertexType == null ? "null" : vertexType.getClass().getName();
    }
}

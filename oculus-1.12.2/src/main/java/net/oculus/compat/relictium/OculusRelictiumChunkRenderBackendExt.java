package net.oculus.compat.relictium;

import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

public interface OculusRelictiumChunkRenderBackendExt {
    void oculus$begin(BlockRenderPass pass);

    void oculus$end();
}

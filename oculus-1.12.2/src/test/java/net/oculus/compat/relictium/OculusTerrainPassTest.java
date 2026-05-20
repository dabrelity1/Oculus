package net.oculus.compat.relictium;

import static org.junit.Assert.assertEquals;

import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.oculus.pipeline.WorldRenderingPhase;
import org.junit.Test;

public class OculusTerrainPassTest {
    @Test
    public void shaderPassSelectionUsesWaterOnlyForTranslucentLayer() {
        assertEquals(OculusTerrainPass.GBUFFER_SOLID, OculusTerrainPass.fromBlockRenderPass(null));
        assertEquals(OculusTerrainPass.GBUFFER_SOLID, OculusTerrainPass.fromBlockRenderPass(BlockRenderPass.SOLID));
        assertEquals(OculusTerrainPass.GBUFFER_SOLID, OculusTerrainPass.fromBlockRenderPass(BlockRenderPass.CUTOUT));
        assertEquals(OculusTerrainPass.GBUFFER_SOLID, OculusTerrainPass.fromBlockRenderPass(BlockRenderPass.CUTOUT_MIPPED));
        assertEquals(OculusTerrainPass.GBUFFER_TRANSLUCENT,
            OculusTerrainPass.fromBlockRenderPass(BlockRenderPass.TRANSLUCENT));
    }

    @Test
    public void worldRenderingPhasePreservesRelictiumTerrainLayer() {
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID, OculusTerrainPass.phaseFromBlockRenderPass(null));
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID,
            OculusTerrainPass.phaseFromBlockRenderPass(BlockRenderPass.SOLID));
        assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT,
            OculusTerrainPass.phaseFromBlockRenderPass(BlockRenderPass.CUTOUT));
        assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED,
            OculusTerrainPass.phaseFromBlockRenderPass(BlockRenderPass.CUTOUT_MIPPED));
        assertEquals(WorldRenderingPhase.TERRAIN_TRANSLUCENT,
            OculusTerrainPass.phaseFromBlockRenderPass(BlockRenderPass.TRANSLUCENT));
    }
}

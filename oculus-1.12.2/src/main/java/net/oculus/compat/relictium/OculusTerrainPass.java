package net.oculus.compat.relictium;

import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.oculus.pipeline.WorldRenderingPhase;

public enum OculusTerrainPass {
    SHADOW("shadow"),
    GBUFFER_SOLID("gbuffers_terrain"),
    GBUFFER_TRANSLUCENT("gbuffers_water");

    private final String programName;

    OculusTerrainPass(String programName) {
        this.programName = programName;
    }

    public String getProgramName() {
        return programName;
    }

    public static OculusTerrainPass fromBlockRenderPass(BlockRenderPass pass) {
        return pass != null && pass.isTranslucent()
            ? GBUFFER_TRANSLUCENT
            : GBUFFER_SOLID;
    }

    public static WorldRenderingPhase phaseFromBlockRenderPass(BlockRenderPass pass) {
        if (pass == null) {
            return WorldRenderingPhase.TERRAIN_SOLID;
        }

        switch (pass) {
            case CUTOUT:
                return WorldRenderingPhase.TERRAIN_CUTOUT;
            case CUTOUT_MIPPED:
                return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
            case TRANSLUCENT:
                return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
            case SOLID:
            default:
                return WorldRenderingPhase.TERRAIN_SOLID;
        }
    }
}

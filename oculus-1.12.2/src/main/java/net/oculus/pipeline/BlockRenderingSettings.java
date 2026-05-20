package net.oculus.pipeline;

import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.oculus.shaderpack.materialmap.NamespacedId;

public final class BlockRenderingSettings {
    public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

    private boolean reloadRequired;
    private Object2IntMap<IBlockState> blockStateIds;
    private Map<Block, BlockRenderLayer> renderLayerOverrides;
    private Object2IntFunction<NamespacedId> entityIds;
    private float ambientOcclusionLevel = 1.0F;
    private boolean disableDirectionalShading;
    private boolean useSeparateAo;
    private boolean useExtendedVertexFormat;

    private BlockRenderingSettings() {
    }

    public boolean isReloadRequired() {
        return reloadRequired;
    }

    public void markReloadRequired() {
        this.reloadRequired = true;
    }

    public void clearReloadRequired() {
        this.reloadRequired = false;
    }

    public float getAmbientOcclusionLevel() {
        return ambientOcclusionLevel;
    }

    public void setAmbientOcclusionLevel(float ambientOcclusionLevel) {
        if (this.ambientOcclusionLevel == ambientOcclusionLevel) {
            return;
        }

        this.ambientOcclusionLevel = ambientOcclusionLevel;
        this.reloadRequired = true;
    }

    public float applyAmbientOcclusionLevel(float originalValue) {
        return 1.0F - ambientOcclusionLevel * (1.0F - originalValue);
    }

    public Object2IntMap<IBlockState> getBlockStateIds() {
        return blockStateIds;
    }

    public void setBlockStateIds(Object2IntMap<IBlockState> blockStateIds) {
        if (this.blockStateIds != null && this.blockStateIds.equals(blockStateIds)) {
            BlockContextHolder.useActiveStateMap(blockStateIds);
            return;
        }

        this.blockStateIds = blockStateIds;
        BlockContextHolder.useActiveStateMap(blockStateIds);
        this.reloadRequired = true;
    }

    public BlockRenderLayer getRenderLayerOverride(Block block) {
        return renderLayerOverrides == null ? null : renderLayerOverrides.get(block);
    }

    public Map<Block, BlockRenderLayer> getRenderLayerOverrides() {
        return renderLayerOverrides;
    }

    public void setRenderLayerOverrides(Map<Block, BlockRenderLayer> renderLayerOverrides) {
        if (this.renderLayerOverrides != null && this.renderLayerOverrides.equals(renderLayerOverrides)) {
            return;
        }

        this.renderLayerOverrides = renderLayerOverrides;
        this.reloadRequired = true;
    }

    public Object2IntFunction<NamespacedId> getEntityIds() {
        return entityIds;
    }

    public void setEntityIds(Object2IntFunction<NamespacedId> entityIds) {
        this.entityIds = entityIds;
    }

    public boolean shouldDisableDirectionalShading() {
        return disableDirectionalShading;
    }

    public void setDisableDirectionalShading(boolean disableDirectionalShading) {
        if (this.disableDirectionalShading == disableDirectionalShading) {
            return;
        }

        this.disableDirectionalShading = disableDirectionalShading;
        this.reloadRequired = true;
    }

    public boolean shouldUseSeparateAo() {
        return useSeparateAo;
    }

    public void setUseSeparateAo(boolean useSeparateAo) {
        if (this.useSeparateAo == useSeparateAo) {
            return;
        }

        this.useSeparateAo = useSeparateAo;
        this.reloadRequired = true;
    }

    public boolean shouldUseExtendedVertexFormat() {
        return useExtendedVertexFormat;
    }

    public void setUseExtendedVertexFormat(boolean useExtendedVertexFormat) {
        if (this.useExtendedVertexFormat == useExtendedVertexFormat) {
            return;
        }

        this.useExtendedVertexFormat = useExtendedVertexFormat;
        this.reloadRequired = true;
    }
}

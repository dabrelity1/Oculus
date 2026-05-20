package net.oculus.mixin.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.oculus.pipeline.BlockRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Applies the OptiFine/Iris ambientOcclusionLevel directive at the shared block-state
 * AO query used by vanilla and most 1.12 block renderers.
 */
@Mixin(value = BlockStateContainer.StateImplementation.class, priority = 990)
public abstract class BlockStateAmbientOcclusionMixin {
    @Shadow
    public abstract Block getBlock();

    /**
     * @author Oculus backport
     * @reason Scale the vanilla block ambient occlusion brightness by the shader pack directive.
     */
    @Overwrite
    public float getAmbientOcclusionLightValue() {
        float originalValue = getBlock().getAmbientOcclusionLightValue((IBlockState) (Object) this);
        return BlockRenderingSettings.INSTANCE.applyAmbientOcclusionLevel(originalValue);
    }
}

package net.oculus.mixin.vertexformat;

import java.util.BitSet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.oculus.pipeline.vertex.SeparateAoTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.BlockModelRenderer$AmbientOcclusionFace")
public abstract class AmbientOcclusionFaceMixin {
    @Shadow
    @Final
    private float[] vertexColorMultiplier;

    @Inject(method = "updateVertexBrightness", at = @At("RETURN"))
    private void oculus$captureSeparateAoMultipliers(IBlockAccess world, IBlockState state, BlockPos pos,
                                                     EnumFacing facing, float[] shape, BitSet bounds,
                                                     CallbackInfo ci) {
        SeparateAoTracker.capture(this.vertexColorMultiplier);
    }
}

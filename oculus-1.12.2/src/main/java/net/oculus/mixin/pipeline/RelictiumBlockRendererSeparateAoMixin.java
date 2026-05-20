package net.oculus.mixin.pipeline;

import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.oculus.pipeline.BlockRenderingSettings;
import net.oculus.pipeline.vertex.RelictiumSeparateAoColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class RelictiumBlockRendererSeparateAoMixin {
    @Unique
    private boolean oculus$useSeparateAo;

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void oculus$cacheSeparateAoSetting(IBlockAccess world, IBlockState state, BlockPos pos, IBakedModel model,
                                               ChunkModelBuffers buffers, boolean cull, long seed,
                                               CallbackInfoReturnable<Boolean> cir) {
        this.oculus$useSeparateAo = BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }

    @Redirect(method = "renderQuad", at = @At(value = "INVOKE",
        target = "Lme/jellysquid/mods/sodium/client/util/color/ColorABGR;mul(IF)I", remap = false), remap = false)
    private int oculus$applySeparateAo(int color, float ao) {
        return RelictiumSeparateAoColor.apply(color, ao, this.oculus$useSeparateAo);
    }
}

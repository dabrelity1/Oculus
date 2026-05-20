package net.oculus.mixin.vertexformat;

import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.oculus.pipeline.vertex.SeparateAoTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererSeparateAoMixin {
    @Redirect(
        method = "renderQuadsFlat",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/model/pipeline/LightUtil;diffuseLight(Lnet/minecraft/util/EnumFacing;)F",
            remap = false
        )
    )
    private float oculus$captureFlatSeparateAo(EnumFacing facing) {
        float multiplier = LightUtil.diffuseLight(facing);
        SeparateAoTracker.captureFlat(multiplier);
        return multiplier;
    }
}

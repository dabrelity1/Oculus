package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.layer.GbufferPrograms;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.SpecialCondition;
import net.oculus.pipeline.WorldRenderingPipeline;

@Mixin(TileEntityBeaconRenderer.class)
public abstract class TileEntityBeaconRendererMixin {
    @Inject(
        method = "renderBeamSegment(DDDDDDII[FDD)V",
        at = @At("HEAD")
    )
    private static void oculus$beginBeaconBeam(
        double x,
        double y,
        double z,
        double partialTicks,
        double textureScale,
        double totalWorldTime,
        int yOffset,
        int height,
        float[] colors,
        double innerRadius,
        double outerRadius,
        CallbackInfo ci
    ) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
    }

    @Inject(
        method = "renderBeamSegment(DDDDDDII[FDD)V",
        at = @At("RETURN")
    )
    private static void oculus$endBeaconBeam(
        double x,
        double y,
        double z,
        double partialTicks,
        double textureScale,
        double totalWorldTime,
        int yOffset,
        int height,
        float[] colors,
        double innerRadius,
        double outerRadius,
        CallbackInfo ci
    ) {
        GbufferPrograms.teardownSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
    }

    @Redirect(
        method = "renderBeamSegment(DDDDDDII[FDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;depthMask(Z)V", ordinal = 0)
    )
    private static void oculus$setInnerBeaconBeamDepthMask(boolean vanillaDepthMask) {
        GlStateManager.depthMask(shouldWriteBeaconBeamToDepthBuffer());
    }

    private static boolean shouldWriteBeaconBeamToDepthBuffer() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        return pipeline == null || pipeline.shouldWriteBeaconBeamToDepthBuffer();
    }
}

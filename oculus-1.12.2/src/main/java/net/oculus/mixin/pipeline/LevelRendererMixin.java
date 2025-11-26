package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.Oculus;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;

/**
 * Bridges the vanilla world render loop to the Oculus shader pipeline. The 1.16.5 mixin
 * targeted {@code LevelRenderer}; on 1.12.2 the equivalent entrypoint is
 * {@code EntityRenderer#renderWorld}.
 */
@Mixin(EntityRenderer.class)
public abstract class LevelRendererMixin {
    private static boolean debugLogged = false;
    
    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void oculus$beginWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!debugLogged) {
            Oculus.LOGGER.info("[Oculus] Pipeline active - intercepting world rendering");
            debugLogged = true;
        }
        PipelineManager.INSTANCE.beginWorldRendering(partialTicks);
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void oculus$endWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.finalizeLevelRendering();
        }
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("HEAD"))
    private void oculus$beginWeather(float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.RAIN_SNOW);
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("RETURN"))
    private void oculus$endWeather(float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderHand(FI)V", at = @At("HEAD"))
    private void oculus$beginHand(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPhase phase = pass == 0 ? WorldRenderingPhase.HAND_SOLID : WorldRenderingPhase.HAND_TRANSLUCENT;
        setPhase(phase);
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.beginHand();
        }
    }

    @Inject(method = "renderHand(FI)V", at = @At("RETURN"))
    private void oculus$endHand(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    private static void setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }
}

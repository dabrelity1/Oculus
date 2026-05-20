package net.oculus.mixin.pipeline;

import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.shaderpack.CloudSetting;

@Mixin(value = GameSettings.class, priority = 1010)
public abstract class GameSettingsCloudsMixin {
    @Shadow
    public int renderDistanceChunks;

    @Inject(method = "shouldRenderClouds()I", at = @At("HEAD"), cancellable = true)
    private void oculus$overrideCloudMode(CallbackInfoReturnable<Integer> cir) {
        if (renderDistanceChunks < 4) {
            return;
        }

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline == null) {
            return;
        }

        CloudSetting setting = pipeline.getCloudSetting();
        switch (setting) {
            case OFF:
                cir.setReturnValue(0);
                return;
            case FAST:
                cir.setReturnValue(1);
                return;
            case FANCY:
                cir.setReturnValue(2);
                return;
            case DEFAULT:
            default:
        }
    }
}

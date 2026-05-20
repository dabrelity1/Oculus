package net.oculus.mixin.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.client.OculusRuntimeValidation;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererValidationMixin {
    @Shadow
    @Final
    private Minecraft mc;

    @Inject(method = "updateCameraAndRender(FJ)V", at = @At("RETURN"))
    private void oculus$captureValidationGuiScreenshot(float partialTicks, long nanoTime, CallbackInfo ci) {
        OculusRuntimeValidation.captureInventoryScreenshotAfterGuiRender(mc);
    }
}

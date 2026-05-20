package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.blending.BlendModeStorage;
import net.oculus.gl.state.StateUpdateNotifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerBlendOverrideMixin {
    @Inject(method = "disableBlend()V", at = @At("HEAD"), cancellable = true)
    private static void oculus$lockBlendDisable(CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(false);
            ci.cancel();
        }
    }

    @Inject(method = "disableBlend()V", at = @At("RETURN"))
    private static void oculus$notifyBlendDisable(CallbackInfo ci) {
        StateUpdateNotifiers.notifyBlendFuncChanged();
    }

    @Inject(method = "enableBlend()V", at = @At("HEAD"), cancellable = true)
    private static void oculus$lockBlendEnable(CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(true);
            ci.cancel();
        }
    }

    @Inject(method = "enableBlend()V", at = @At("RETURN"))
    private static void oculus$notifyBlendEnable(CallbackInfo ci) {
        StateUpdateNotifiers.notifyBlendFuncChanged();
    }

    @Inject(method = "blendFunc(II)V", at = @At("HEAD"), cancellable = true)
    private static void oculus$lockBlendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            ci.cancel();
        }
    }

    @Inject(method = "blendFunc(II)V", at = @At("RETURN"))
    private static void oculus$notifyBlendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        StateUpdateNotifiers.notifyBlendFuncChanged();
    }

    @Inject(method = "tryBlendFuncSeparate(IIII)V", at = @At("HEAD"), cancellable = true)
    private static void oculus$lockBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            ci.cancel();
        }
    }

    @Inject(method = "tryBlendFuncSeparate(IIII)V", at = @At("RETURN"))
    private static void oculus$notifyBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        StateUpdateNotifiers.notifyBlendFuncChanged();
    }
}

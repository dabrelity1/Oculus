package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.pipeline.state.StateTracker;
import net.oculus.uniforms.BuiltinReplacementUniforms;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerStateMixin {
    @Shadow
    private static int activeTextureUnit;

    private static boolean oculus$lockBindTextureCallback;

    @Inject(method = "enableTexture2D()V", at = @At("HEAD"))
    private static void oculus$onEnableTexture(CallbackInfo ci) {
        updateTextureAvailability(true);
    }

    @Inject(method = "disableTexture2D()V", at = @At("HEAD"))
    private static void oculus$onDisableTexture(CallbackInfo ci) {
        updateTextureAvailability(false);
    }

    @Inject(method = "bindTexture(I)V", at = @At("RETURN"))
    private static void oculus$onBindTexture(int texture, CallbackInfo ci) {
        if (oculus$lockBindTextureCallback
            || OculusRenderSystem.isTextureBindCallbackSuppressed()
            || activeTextureUnit != 0) {
            return;
        }

        oculus$lockBindTextureCallback = true;
        try {
            Throwable failure = null;
            failure = runBindTextureCallback(failure, StateUpdateNotifiers::notifyTextureBindingChanged);

            WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
            if (pipeline != null) {
                failure = runBindTextureCallback(failure, () -> pipeline.onBindTexture(texture));
            }

            failure = runBindTextureCallback(failure, () -> GlStateManager.bindTexture(texture));
            rethrowBindTextureCallbackFailure(failure);
        } finally {
            oculus$lockBindTextureCallback = false;
        }
    }

    private static Throwable runBindTextureCallback(Throwable failure, Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                if (exception != failure) {
                    failure.addSuppressed(exception);
                }
                return failure;
            }
            return exception;
        }
        return failure;
    }

    private static void rethrowBindTextureCallbackFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }

    @Inject(method = "enableFog()V", at = @At("RETURN"))
    private static void oculus$onEnableFog(CallbackInfo ci) {
        StateUpdateNotifiers.notifyFogToggled();
    }

    @Inject(method = "disableFog()V", at = @At("RETURN"))
    private static void oculus$onDisableFog(CallbackInfo ci) {
        StateUpdateNotifiers.notifyFogToggled();
    }

    @Inject(method = "setFog(Lnet/minecraft/client/renderer/GlStateManager$FogMode;)V", at = @At("RETURN"))
    private static void oculus$onSetFog(GlStateManager.FogMode fogMode, CallbackInfo ci) {
        StateUpdateNotifiers.notifyFogModeChanged();
    }

    @Inject(method = "setFogDensity(F)V", at = @At("RETURN"))
    private static void oculus$onSetFogDensity(float density, CallbackInfo ci) {
        StateUpdateNotifiers.notifyFogDensityChanged();
    }

    @Inject(method = "setFogStart(F)V", at = @At("RETURN"))
    private static void oculus$onSetFogStart(float start, CallbackInfo ci) {
        StateUpdateNotifiers.notifyFogStartChanged();
    }

    @Inject(method = "setFogEnd(F)V", at = @At("RETURN"))
    private static void oculus$onSetFogEnd(float end, CallbackInfo ci) {
        StateUpdateNotifiers.notifyFogEndChanged();
    }

    @Inject(method = "color(FFFF)V", at = @At("RETURN"))
    private static void oculus$onColor(float red, float green, float blue, float alpha, CallbackInfo ci) {
        BuiltinReplacementUniforms.setColorModulator(red, green, blue, alpha);
    }

    @Inject(method = "color(FFF)V", at = @At("RETURN"))
    private static void oculus$onColor(float red, float green, float blue, CallbackInfo ci) {
        BuiltinReplacementUniforms.setColorModulator(red, green, blue, 1.0F);
    }

    @Inject(method = "glDrawArrays(III)V", at = @At("HEAD"))
    private static void oculus$beforeDrawArrays(int mode, int first, int count, CallbackInfo ci) {
        syncPipelineProgram();
    }

    @Inject(method = "callList(I)V", at = @At("HEAD"))
    private static void oculus$beforeCallList(int list, CallbackInfo ci) {
        syncPipelineProgram();
    }

    private static void syncPipelineProgram() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.syncProgram();
        }
    }

    private static void updateTextureAvailability(boolean enabled) {
        int lightmapUnit = OpenGlHelper.lightmapTexUnit - OpenGlHelper.defaultTexUnit;
        int overlayUnit = OpenGlHelper.GL_TEXTURE2 - OpenGlHelper.defaultTexUnit;
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - OpenGlHelper.defaultTexUnit;
        if (activeUnit < 0) {
            activeUnit = activeTextureUnit;
        }

        if (activeUnit == 0) {
            StateTracker.INSTANCE.albedoSampler = enabled;
        } else if (activeUnit == lightmapUnit) {
            StateTracker.INSTANCE.lightmapSampler = enabled;
        } else if (activeUnit == overlayUnit) {
            StateTracker.INSTANCE.overlaySampler = enabled;
        } else {
            return;
        }

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setInputs(StateTracker.INSTANCE.getInputs());
        }
    }
}

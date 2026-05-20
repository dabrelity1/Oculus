package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.texture.format.TextureFormatLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class TextureManagerPBRReloadMixin {
    @Inject(method = "onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("TAIL"))
    private void oculus$onResourceManagerReload(IResourceManager resourceManager, CallbackInfo ci) {
        Throwable failure = null;
        try {
            TextureFormatLoader.reload(resourceManager);
        } catch (RuntimeException | Error exception) {
            failure = exception;
        }

        try {
            resetActivePbrTextureBindings();
        } catch (RuntimeException | Error exception) {
            failure = collectReloadFailure(failure, exception);
        }

        rethrowReloadFailure(failure);
    }

    private static void resetActivePbrTextureBindings() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.resetPbrTextureBindings();
        }
    }

    private static Throwable collectReloadFailure(Throwable failure, Throwable exception) {
        if (failure != null) {
            if (exception != failure) {
                failure.addSuppressed(exception);
            }
            return failure;
        }
        return exception;
    }

    private static void rethrowReloadFailure(Throwable failure) {
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
}

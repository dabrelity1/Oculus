package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.shadow.ShadowRenderingState;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.IdMapUniforms;

@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {
    @Shadow
    private boolean renderShadow;

    @Shadow
    public Entity renderViewEntity;

    @Shadow
    public GameSettings options;

    @Inject(method = "renderEntityStatic(Lnet/minecraft/entity/Entity;FZ)V", at = @At("HEAD"), cancellable = true)
    private void oculus$cancelShadowStaticEntity(Entity entity, float partialTicks, boolean hideDebugBox,
                                                 CallbackInfo ci) {
        if (!ShadowRenderingState.shouldRenderEntity(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMultipass(Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"), cancellable = true)
    private void oculus$cancelShadowMultipassEntity(Entity entity, float partialTicks, CallbackInfo ci) {
        if (!ShadowRenderingState.shouldRenderEntity(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("HEAD"), cancellable = true)
    private void oculus$captureEntity(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                      boolean renderHitBoxes, CallbackInfo ci) {
        if (!ShadowRenderingState.shouldRenderEntity(entity)) {
            ci.cancel();
            return;
        }
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.head");
        OculusRuntimeValidation.logLocalPlayerEntityRender(
            entity,
            this.renderViewEntity,
            this.options == null ? -1 : this.options.thirdPersonView,
            ShadowRenderingState.isActive(),
            x,
            y,
            z);
        CapturedRenderingState.INSTANCE.setCurrentEntity(IdMapUniforms.resolveEntityId(entity));
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.afterEntityId");
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("RETURN"))
    private void oculus$clearCapturedEntity(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                            boolean renderHitBoxes, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.return");
        CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.afterClearEntityId");
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V",
            shift = At.Shift.BEFORE))
    private void oculus$beforeEntityRenderer(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                             boolean renderHitBoxes, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.beforeDoRender");
        Render<?> renderer = ((RenderManager) (Object) this).getEntityRenderObject(entity);
        OculusRuntimeValidation.logLocalPlayerRendererCall(
            entity,
            renderer == null ? "null" : renderer.getClass().getSimpleName(),
            ShadowRenderingState.isActive());
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V",
            shift = At.Shift.AFTER))
    private void oculus$afterEntityRenderer(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                            boolean renderHitBoxes, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.afterDoRender."
            + entity.getClass().getSimpleName());
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V",
            shift = At.Shift.AFTER))
    private void oculus$afterEntityShadowAndFire(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                                 boolean renderHitBoxes, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderManager.renderEntity.afterShadowAndFire");
    }

    @Redirect(
        method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V"
        )
    )
    private void oculus$maybeSuppressVanillaEntityShadow(Render<Entity> renderer, Entity entity, double x, double y,
                                                        double z, float entityYaw, float partialTicks) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null && pipeline.shouldDisableVanillaEntityShadows()) {
            boolean previousRenderShadow = this.renderShadow;
            this.renderShadow = false;
            try {
                renderer.doRenderShadowAndFire(entity, x, y, z, entityYaw, partialTicks);
            } finally {
                this.renderShadow = previousRenderShadow;
            }
            return;
        }

        renderer.doRenderShadowAndFire(entity, x, y, z, entityYaw, partialTicks);
    }
}

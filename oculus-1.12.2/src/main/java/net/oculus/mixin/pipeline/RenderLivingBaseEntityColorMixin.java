package net.oculus.mixin.pipeline;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.ShaderWorldRenderingPipeline;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.uniforms.GameplayUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL11;

@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseEntityColorMixin {
    @Shadow
    protected FloatBuffer brightnessBuffer;

    @Shadow
    protected abstract int getColorMultiplier(EntityLivingBase entity, float lightBrightness, float partialTickTime);

    @Unique
    private boolean oculus$restoreBlendAfterShaderEntityBaseModel;

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void oculus$beforeLivingRender(EntityLivingBase entity, double x, double y, double z,
                                           float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$restoreBlendAfterShaderEntityBaseModel = false;
        oculus$drainEntityCheckpoint("renderLivingBase.doRender.head", entity);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            ordinal = 0,
            shift = At.Shift.BEFORE))
    private void oculus$beforePrimaryModelRender(EntityLivingBase entity, double x, double y, double z,
                                                 float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$drainEntityCheckpoint("renderLivingBase.renderModel0.before", entity);
        oculus$restoreOpaqueWhiteColorForShaderEntityBaseModel();
        oculus$resetShaderEntityTextureMatrix();
        oculus$disableBlendForShaderEntityBaseModel();
        oculus$syncShaderEntityProgramForBaseModel();
        OculusRuntimeValidation.logLocalPlayerModelRender(entity, "primary-draw-state");
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            ordinal = 0,
            shift = At.Shift.AFTER))
    private void oculus$afterPrimaryModelRender(EntityLivingBase entity, double x, double y, double z,
                                                float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$restoreBlendAfterShaderEntityBaseModel();
        oculus$drainEntityCheckpoint("renderLivingBase.renderModel0.after", entity);
        oculus$syncShaderEntityProgramForBaseModel();
        OculusRuntimeValidation.logLocalPlayerModelRender(entity, "primary-after");
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            ordinal = 1,
            shift = At.Shift.BEFORE))
    private void oculus$beforeSecondaryModelRender(EntityLivingBase entity, double x, double y, double z,
                                                   float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$drainEntityCheckpoint("renderLivingBase.renderModel1.before", entity);
        oculus$restoreOpaqueWhiteColorForShaderEntityBaseModel();
        oculus$resetShaderEntityTextureMatrix();
        oculus$disableBlendForShaderEntityBaseModel();
        oculus$syncShaderEntityProgramForBaseModel();
        OculusRuntimeValidation.logLocalPlayerModelRender(entity, "normal-draw-state");
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            ordinal = 1,
            shift = At.Shift.AFTER))
    private void oculus$afterSecondaryModelRender(EntityLivingBase entity, double x, double y, double z,
                                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$restoreBlendAfterShaderEntityBaseModel();
        oculus$drainEntityCheckpoint("renderLivingBase.renderModel1.after", entity);
        oculus$syncShaderEntityProgramForBaseModel();
        OculusRuntimeValidation.logLocalPlayerModelRender(entity, "normal-after");
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;renderLayers(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V",
            ordinal = 0,
            shift = At.Shift.AFTER))
    private void oculus$afterPrimaryLayerRender(EntityLivingBase entity, double x, double y, double z,
                                                float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$drainEntityCheckpoint("renderLivingBase.renderLayers0.after", entity);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;renderLayers(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V",
            ordinal = 1,
            shift = At.Shift.AFTER))
    private void oculus$afterSecondaryLayerRender(EntityLivingBase entity, double x, double y, double z,
                                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        oculus$drainEntityCheckpoint("renderLivingBase.renderLayers1.after", entity);
    }

    @Inject(method = "setBrightness(Lnet/minecraft/entity/EntityLivingBase;FZ)Z", at = @At("RETURN"))
    private void oculus$captureEntityColor(EntityLivingBase entity, float partialTicks, boolean combineTextures,
                                           CallbackInfoReturnable<Boolean> cir) {
        oculus$drainEntityCheckpoint("renderLivingBase.setBrightness.return", entity);
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || brightnessBuffer == null || brightnessBuffer.limit() < 4) {
            GameplayUniforms.clearEntityColor();
            return;
        }

        GameplayUniforms.setEntityColor(
            brightnessBuffer.get(0),
            brightnessBuffer.get(1),
            brightnessBuffer.get(2),
            brightnessBuffer.get(3));
    }

    @Inject(method = "unsetBrightness()V", at = @At("RETURN"))
    private void oculus$clearEntityColor(CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderLivingBase.unsetBrightness.return");
        GameplayUniforms.clearEntityColor();
    }

    @Inject(method = "setBrightness(Lnet/minecraft/entity/EntityLivingBase;FZ)Z", at = @At("HEAD"), cancellable = true)
    private void oculus$setShaderEntityColor(EntityLivingBase entity, float partialTicks, boolean combineTextures,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!oculus$shouldReplaceVanillaBrightnessCombiner()) {
            return;
        }

        float brightness = entity.getBrightness();
        int colorMultiplier = getColorMultiplier(entity, brightness, partialTicks);
        boolean hasColorMultiplier = (colorMultiplier >> 24 & 255) > 0;
        boolean hurt = entity.hurtTime > 0 || entity.deathTime > 0;

        if (!hasColorMultiplier && !hurt) {
            GameplayUniforms.clearEntityColor();
            cir.setReturnValue(false);
            return;
        }

        if (!hasColorMultiplier && !combineTextures) {
            GameplayUniforms.clearEntityColor();
            cir.setReturnValue(false);
            return;
        }

        if (hurt) {
            GameplayUniforms.setEntityColor(1.0F, 0.0F, 0.0F, 0.3F);
        } else {
            float alpha = (float) (colorMultiplier >> 24 & 255) / 255.0F;
            float red = (float) (colorMultiplier >> 16 & 255) / 255.0F;
            float green = (float) (colorMultiplier >> 8 & 255) / 255.0F;
            float blue = (float) (colorMultiplier & 255) / 255.0F;
            GameplayUniforms.setEntityColor(red, green, blue, 1.0F - alpha);
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "unsetBrightness()V", at = @At("HEAD"), cancellable = true)
    private void oculus$clearShaderEntityColor(CallbackInfo ci) {
        if (!oculus$shouldReplaceVanillaBrightnessCombiner()) {
            return;
        }

        GameplayUniforms.clearEntityColor();
        ci.cancel();
    }

    @Redirect(method = "renderLayers(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/LayerRenderer;doRenderLayer(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V"))
    private void oculus$renderLayerWithValidationCheckpoint(LayerRenderer layer, EntityLivingBase entity,
                                                           float limbSwing, float limbSwingAmount, float partialTicks,
                                                           float ageInTicks, float netHeadYaw, float headPitch,
                                                           float scale) {
        oculus$resetShaderEntityTextureMatrix();
        oculus$syncShaderEntityProgramForBaseModel();
        layer.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderLivingBase.layer.after."
            + layer.getClass().getSimpleName() + "." + entity.getClass().getSimpleName());
    }

    private static boolean oculus$shouldReplaceVanillaBrightnessCombiner() {
        return PipelineManager.INSTANCE.getPipelineNullable() instanceof ShaderWorldRenderingPipeline
            && PipelineManager.INSTANCE.getPipelineNullable().getPhase() == WorldRenderingPhase.ENTITIES;
    }

    @Unique
    private void oculus$disableBlendForShaderEntityBaseModel() {
        oculus$restoreBlendAfterShaderEntityBaseModel = false;
        if (!oculus$shouldReplaceVanillaBrightnessCombiner() || !GL11.glIsEnabled(GL11.GL_BLEND)) {
            return;
        }

        GlStateManager.disableBlend();
        oculus$restoreBlendAfterShaderEntityBaseModel = true;
    }

    @Unique
    private void oculus$restoreOpaqueWhiteColorForShaderEntityBaseModel() {
        if (!oculus$shouldReplaceVanillaBrightnessCombiner()) {
            return;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    }

    @Unique
    private void oculus$resetShaderEntityTextureMatrix() {
        if (!oculus$shouldReplaceVanillaBrightnessCombiner()) {
            return;
        }

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(previousMatrixMode);
        GlStateManager.setActiveTexture(previousActiveTexture);
    }

    @Unique
    private void oculus$syncShaderEntityProgramForBaseModel() {
        if (!oculus$shouldReplaceVanillaBrightnessCombiner()) {
            return;
        }

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline instanceof ShaderWorldRenderingPipeline) {
            ((ShaderWorldRenderingPipeline) pipeline).syncEntityProgramForLegacyDraw();
        }
    }

    @Unique
    private void oculus$restoreBlendAfterShaderEntityBaseModel() {
        if (!oculus$restoreBlendAfterShaderEntityBaseModel) {
            return;
        }

        GlStateManager.enableBlend();
        oculus$restoreBlendAfterShaderEntityBaseModel = false;
    }

    private static void oculus$drainEntityCheckpoint(String checkpoint, EntityLivingBase entity) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint(checkpoint + "." + entity.getClass().getSimpleName());
    }
}

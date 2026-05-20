package net.oculus.mixin.pipeline;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.ShaderWorldRenderingPipeline;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Keeps the player model on the opaque entity/hand paths when shader packs are active.
 */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerShaderStateMixin {
    @Shadow
    public abstract ModelPlayer getMainModel();

    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderPlayer;setModelVisibilities(Lnet/minecraft/client/entity/AbstractClientPlayer;)V",
            shift = At.Shift.AFTER))
    private void oculus$afterPlayerVisibilitySetup(AbstractClientPlayer player, double x, double y, double z,
                                                   float entityYaw, float partialTicks, CallbackInfo ci) {
        if (oculus$isShaderEntityPhase() && !player.isSpectator()) {
            oculus$forceBasePlayerPartsVisible(getMainModel());
        }
        OculusRuntimeValidation.logLocalPlayerModelParts(player, getMainModel(), "after-player-visibilities");
    }

    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLivingBase;doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            shift = At.Shift.BEFORE))
    private void oculus$beforePlayerBaseRender(AbstractClientPlayer player, double x, double y, double z,
                                               float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!oculus$isShaderEntityPhase()) {
            return;
        }

        oculus$forceOpaqueDepthWritingState();
        oculus$syncShaderEntityProgram();
        OculusRuntimeValidation.logLocalPlayerModelParts(player, getMainModel(), "before-render-living-base");
    }

    @Inject(method = "renderRightArm(Lnet/minecraft/client/entity/AbstractClientPlayer;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;enableBlend()V",
            shift = At.Shift.AFTER))
    private void oculus$beforeRightArmModel(AbstractClientPlayer player, CallbackInfo ci) {
        oculus$prepareShaderHandModel(player, "right-arm");
    }

    @Inject(method = "renderLeftArm(Lnet/minecraft/client/entity/AbstractClientPlayer;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;enableBlend()V",
            shift = At.Shift.AFTER))
    private void oculus$beforeLeftArmModel(AbstractClientPlayer player, CallbackInfo ci) {
        oculus$prepareShaderHandModel(player, "left-arm");
    }

    @Unique
    private static boolean oculus$isShaderEntityPhase() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        return pipeline instanceof ShaderWorldRenderingPipeline
            && pipeline.getPhase() == WorldRenderingPhase.ENTITIES;
    }

    @Unique
    private static boolean oculus$isShaderHandPhase(WorldRenderingPipeline pipeline) {
        return pipeline instanceof ShaderWorldRenderingPipeline
            && (pipeline.getPhase() == WorldRenderingPhase.HAND_SOLID
                || pipeline.getPhase() == WorldRenderingPhase.HAND_TRANSLUCENT);
    }

    @Unique
    private static void oculus$forceBasePlayerPartsVisible(ModelPlayer model) {
        if (model == null) {
            return;
        }

        oculus$showModelPart(model.bipedHead);
        oculus$showModelPart(model.bipedHeadwear);
        oculus$showModelPart(model.bipedBody);
        oculus$showModelPart(model.bipedRightArm);
        oculus$showModelPart(model.bipedLeftArm);
        oculus$showModelPart(model.bipedRightLeg);
        oculus$showModelPart(model.bipedLeftLeg);
    }

    @Unique
    private static void oculus$showModelPart(ModelRenderer part) {
        if (part == null) {
            return;
        }

        part.showModel = true;
        part.isHidden = false;
    }

    @Unique
    private static void oculus$forceOpaqueDepthWritingState() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        oculus$resetDefaultTextureMatrix();
    }

    @Unique
    private static void oculus$syncShaderEntityProgram() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline instanceof ShaderWorldRenderingPipeline) {
            ((ShaderWorldRenderingPipeline) pipeline).syncEntityProgramForLegacyDraw();
        }
    }

    @Unique
    private static void oculus$prepareShaderHandModel(AbstractClientPlayer player, String stage) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (!oculus$isShaderHandPhase(pipeline)) {
            return;
        }

        oculus$forceOpaqueDepthWritingState();
        ((ShaderWorldRenderingPipeline) pipeline).syncProgram();
        OculusRuntimeValidation.logLocalPlayerHandRender(player, stage);
    }

    @Unique
    private static void oculus$resetDefaultTextureMatrix() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(previousMatrixMode);
        GlStateManager.setActiveTexture(previousActiveTexture);
    }
}

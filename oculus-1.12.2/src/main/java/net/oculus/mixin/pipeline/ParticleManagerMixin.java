package net.oculus.mixin.pipeline;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "renderParticles(Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"))
    private void oculus$beginParticles(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.PARTICLES);
    }

    @Inject(method = "renderParticles(Lnet/minecraft/entity/Entity;F)V", at = @At("RETURN"))
    private void oculus$endParticles(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    private static void setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }
}

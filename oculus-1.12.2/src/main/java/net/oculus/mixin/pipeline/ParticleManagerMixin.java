package net.oculus.mixin.pipeline;

import java.util.ArrayDeque;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.pipeline.particle.PhasedParticleManager;
import net.oculus.pipeline.particle.ParticleRenderingPhase;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin implements PhasedParticleManager {
    @Shadow
    @Final
    private ArrayDeque<Particle>[][] fxLayers;

    @Unique
    private ParticleRenderingPhase oculus$particleRenderingPhase = ParticleRenderingPhase.EVERYTHING;

    @Inject(method = "renderParticles(Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"))
    private void oculus$beginParticles(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.PARTICLES);
    }

    @Inject(method = "renderParticles(Lnet/minecraft/entity/Entity;F)V", at = @At("RETURN"))
    private void oculus$endParticles(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderLitParticles(Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"), cancellable = true)
    private void oculus$beginLitParticles(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.PARTICLES);
        if (oculus$particleRenderingPhase == ParticleRenderingPhase.TRANSLUCENT) {
            setPhase(WorldRenderingPhase.NONE);
            ci.cancel();
        }
    }

    @Inject(method = "renderLitParticles(Lnet/minecraft/entity/Entity;F)V", at = @At("RETURN"))
    private void oculus$endLitParticles(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Redirect(
        method = "renderParticles(Lnet/minecraft/entity/Entity;F)V",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayDeque;isEmpty()Z", remap = false)
    )
    private boolean oculus$skipParticleBucket(ArrayDeque<Particle> particles) {
        return particles.isEmpty() || oculus$shouldSkipParticleBucket(particles);
    }

    @Override
    public void setParticleRenderingPhase(ParticleRenderingPhase phase) {
        this.oculus$particleRenderingPhase = phase == null ? ParticleRenderingPhase.EVERYTHING : phase;
    }

    @Unique
    private boolean oculus$shouldSkipParticleBucket(ArrayDeque<Particle> particles) {
        if (oculus$particleRenderingPhase == ParticleRenderingPhase.EVERYTHING) {
            return false;
        }

        int depthBucket = oculus$getDepthBucket(particles);
        if (depthBucket < 0) {
            return false;
        }

        if (oculus$particleRenderingPhase == ParticleRenderingPhase.OPAQUE) {
            return depthBucket == 0;
        }

        return depthBucket != 0;
    }

    @Unique
    private int oculus$getDepthBucket(ArrayDeque<Particle> particles) {
        for (int layer = 0; layer < fxLayers.length; layer++) {
            ArrayDeque<Particle>[] layerBuckets = fxLayers[layer];
            for (int bucket = 0; bucket < layerBuckets.length; bucket++) {
                if (layerBuckets[bucket] == particles) {
                    return bucket;
                }
            }
        }

        return -1;
    }

    private static void setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }
}

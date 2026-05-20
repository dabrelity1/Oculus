package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Invoker("renderHand")
    void oculus$invokeRenderHand(float partialTicks, int pass);
}

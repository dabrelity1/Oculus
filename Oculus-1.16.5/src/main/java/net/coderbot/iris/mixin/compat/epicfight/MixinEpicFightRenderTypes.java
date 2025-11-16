package net.coderbot.iris.mixin.compat.epicfight;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;

@Mixin(EpicFightRenderTypes.class)
public class MixinEpicFightRenderTypes {
    @Inject(method = "getArmorVertexBuilder", at = @At("HEAD"), cancellable = true)
    private static void getArmorVertexBuilder(MultiBufferSource buffer, RenderType renderType, boolean withGlint, CallbackInfoReturnable<VertexConsumer> cir) {
        if(EpicFightRenderTypes.enchantedAnimatedArmor() == renderType) cir.setReturnValue(buffer.getBuffer(renderType));
    }
}

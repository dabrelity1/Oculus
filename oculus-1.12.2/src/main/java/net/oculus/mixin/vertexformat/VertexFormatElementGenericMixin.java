package net.oculus.mixin.vertexformat;

import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexFormatElement.class)
public abstract class VertexFormatElementGenericMixin {
    @Inject(method = "isFirstOrUV", at = @At("HEAD"), cancellable = true)
    private void oculus$allowGenericAttributes(int index, VertexFormatElement.EnumUsage usage,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (usage == VertexFormatElement.EnumUsage.GENERIC) {
            cir.setReturnValue(true);
        }
    }
}

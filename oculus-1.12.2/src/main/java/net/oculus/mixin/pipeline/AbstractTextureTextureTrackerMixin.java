package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.oculus.texture.TextureTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public abstract class AbstractTextureTextureTrackerMixin {
    @Shadow
    protected int glTextureId;

    @Inject(
        method = "getGlTextureId()I",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;glTextureId:I",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void oculus$afterGenerateTextureId(CallbackInfoReturnable<Integer> cir) {
        TextureTracker.INSTANCE.trackTexture(glTextureId, (AbstractTexture) (Object) this);
    }
}

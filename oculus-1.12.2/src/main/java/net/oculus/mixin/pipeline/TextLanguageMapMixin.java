package net.oculus.mixin.pipeline;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.oculus.shaderpack.ShaderPackLanguageLookup;

@Mixin(value = net.minecraft.util.text.translation.LanguageMap.class, priority = 990)
public abstract class TextLanguageMapMixin {
    @Shadow
    @Final
    private Map<String, String> languageList;

    @Inject(method = "translateKey(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void oculus$translateShaderPackKey(String key, CallbackInfoReturnable<String> cir) {
        if (oculus$hasVanillaTranslation(key)) {
            return;
        }

        String translation = ShaderPackLanguageLookup.lookupActive(key);
        if (translation != null) {
            cir.setReturnValue(translation);
        }
    }

    @Inject(method = "translateKeyFormat(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void oculus$formatShaderPackKey(String key, Object[] parameters, CallbackInfoReturnable<String> cir) {
        if (oculus$hasVanillaTranslation(key)) {
            return;
        }

        String translation = ShaderPackLanguageLookup.lookupActive(key);
        if (translation != null) {
            cir.setReturnValue(ShaderPackLanguageLookup.formatVanilla(translation, parameters));
        }
    }

    @Inject(method = "isKeyTranslated(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void oculus$hasShaderPackKey(String key, CallbackInfoReturnable<Boolean> cir) {
        if (oculus$hasVanillaTranslation(key)) {
            return;
        }

        if (ShaderPackLanguageLookup.lookupActive(key) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getLastUpdateTimeInMilliseconds()J", at = @At("RETURN"), cancellable = true)
    private void oculus$includeShaderPackReloadVersion(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(ShaderPackLanguageLookup.getTextComponentLanguageVersion(cir.getReturnValue()));
    }

    private boolean oculus$hasVanillaTranslation(String key) {
        return key == null || this.languageList.containsKey(key);
    }
}

package net.oculus.mixin.pipeline;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.IResourceManager;
import net.oculus.client.IrisLanguageJsonLoader;
import net.oculus.shaderpack.ShaderPackLanguageLookup;

@Mixin(value = net.minecraft.client.resources.Locale.class, priority = 990)
public abstract class ClientLocaleLanguageMixin {
    @Shadow
    private Map<String, String> properties;

    @Inject(method = "loadLocaleDataFiles", at = @At("RETURN"))
    private void oculus$loadBundledIrisJsonTranslations(IResourceManager resourceManager, List<String> languageCodes, CallbackInfo ci) {
        IrisLanguageJsonLoader.loadInto(this.properties, languageCodes);
    }

    @Inject(method = "formatMessage(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void oculus$formatShaderPackTranslation(String key, Object[] parameters, CallbackInfoReturnable<String> cir) {
        if (oculus$hasVanillaTranslation(key)) {
            return;
        }

        String translation = ShaderPackLanguageLookup.lookupActive(key);
        if (translation != null) {
            cir.setReturnValue(ShaderPackLanguageLookup.formatVanilla(translation, parameters));
        }
    }

    @Inject(method = "hasKey(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void oculus$hasShaderPackTranslation(String key, CallbackInfoReturnable<Boolean> cir) {
        if (oculus$hasVanillaTranslation(key)) {
            return;
        }

        if (ShaderPackLanguageLookup.lookupActive(key) != null) {
            cir.setReturnValue(true);
        }
    }

    private boolean oculus$hasVanillaTranslation(String key) {
        return key == null || this.properties.containsKey(key);
    }
}

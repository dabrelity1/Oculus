package net.oculus.mixin.client.gui;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.oculus.gui.ShaderPackScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SodiumOptionsGUI.class)
public abstract class MixinSodiumOptionsGUI extends GuiScreen {

    private OptionPage oculusShaderPackPage;

    @Shadow(remap = false)
    @Final
    private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(GuiScreen prevScreen, CallbackInfo ci) {
        this.oculusShaderPackPage = new OptionPage(
            I18n.format("options.iris.shaderPackSelection"),
            ImmutableList.of()
        );

        this.pages.add(this.oculusShaderPackPage);
    }

    @Inject(method = "setPage", at = @At("HEAD"), remap = false, cancellable = true)
    private void onSetPage(OptionPage page, CallbackInfo ci) {
        if (page == this.oculusShaderPackPage) {
            ci.cancel();
            this.mc.displayGuiScreen(new ShaderPackScreen(this));
        }
    }
}

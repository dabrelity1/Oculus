package net.oculus.mixin.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.oculus.client.gui.GuiShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInitGui(CallbackInfo ci) {
        this.buttonList.add(new GuiButton(8675309, this.width / 2 - 100, this.height / 6 + 168, "Shaders..."));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 8675309) {
            ci.cancel();
            this.mc.displayGuiScreen(new GuiShaders(this));
        }
    }
}

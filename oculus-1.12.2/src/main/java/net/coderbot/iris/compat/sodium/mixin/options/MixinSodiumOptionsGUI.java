package net.coderbot.iris.compat.sodium.mixin.options;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentTranslation;
import net.oculus.client.gui.GuiShaders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Injects the Oculus shader pack screen into the Relictium / Sodium options menu by
 * appending a dedicated tab and intercepting its selection to display {@link GuiShaders}.
 */
@Mixin(SodiumOptionsGUI.class)
public abstract class MixinSodiumOptionsGUI extends GuiScreen {

    @Shadow(remap = false)
    @Final
    private List<OptionPage> pages;

    @Unique
    private OptionPage oculus$shaderPackPage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void oculus$addShaderPackPage(GuiScreen prevScreen, CallbackInfo ci) {
        this.oculus$shaderPackPage = new OptionPage(
                new TextComponentTranslation("options.iris.shaderPackSelection"),
                ImmutableList.of()
        );
        this.pages.add(this.oculus$shaderPackPage);
    }

    @Inject(method = "setPage", at = @At("HEAD"), remap = false, cancellable = true)
    private void oculus$openShaderPackScreen(OptionPage page, CallbackInfo ci) {
        if (page == this.oculus$shaderPackPage) {
            this.mc.displayGuiScreen(new GuiShaders(this));
            ci.cancel();
        }
    }
}

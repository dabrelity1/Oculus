package net.oculus.client.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public class GuiShaders extends GuiScreen {
    private final GuiScreen parentScreen;

    public GuiShaders(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 27, 200, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "Oculus Shader Pack List (WIP)", this.width / 2, 40, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

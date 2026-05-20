package net.oculus.gui.element.widget;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.shaderpack.option.menu.OptionMenuLinkElement;

public class LinkElementWidget extends CommentedElementWidget<OptionMenuLinkElement> {
    private static final ITextComponent ARROW = new TextComponentString(">");

    private final String targetScreenId;

    private ShaderPackScreen screen;
    private NavigationController navigation;
    private ITextComponent label;
    private ITextComponent trimmedLabel;
    private boolean isLabelTrimmed;

    public LinkElementWidget(OptionMenuLinkElement element) {
        super(element);
        this.targetScreenId = element.targetScreenId;
        this.label = new TextComponentString(element.targetScreenId);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.screen = screen;
        this.navigation = navigation;
        this.label = GuiUtil.translateShaderPackOrDefault(
            screen != null ? screen.getCurrentPack() : null,
            new TextComponentString(this.targetScreenId),
            "screen." + this.targetScreenId
        );
        this.trimmedLabel = null;
        this.isLabelTrimmed = false;
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
        GuiUtil.bindIrisWidgetsTexture();
        GuiUtil.drawButton(x, y, width, height, hovered, false);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int maxLabelWidth = width - 18;

        this.isLabelTrimmed = font.getStringWidth(this.label.getFormattedText()) > maxLabelWidth;
        if (this.trimmedLabel == null) {
            this.trimmedLabel = GuiUtil.shortenText(font, this.label, maxLabelWidth);
        }

        String labelText = this.trimmedLabel.getFormattedText();
        int labelWidth = font.getStringWidth(labelText);
        int labelX = x + (width / 2) - (labelWidth / 2) - (Math.max(labelWidth - (width - 18), 0) / 2);
        font.drawStringWithShadow(labelText, labelX, y + 7, 0xFFFFFF);
        font.drawStringWithShadow(ARROW.getFormattedText(), (x + width) - 9, y + 7, 0xFFFFFF);

        if (hovered && this.isLabelTrimmed) {
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, this.label, mouseX + 2, mouseY - 16));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && this.navigation != null) {
            this.navigation.open(this.targetScreenId);
            GuiUtil.playButtonClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public Optional<ITextComponent> getCommentTitle() {
        return Optional.of(this.label);
    }

    @Override
    public Optional<ITextComponent> getCommentBody() {
        String key = "screen." + this.targetScreenId + ".comment";
        if (I18n.hasKey(key)) {
            return Optional.of(new TextComponentTranslation(key));
        }
        ITextComponent shaderPackText = this.screen != null
            ? GuiUtil.translateShaderPack(this.screen.getCurrentPack(), key)
            : null;
        return shaderPackText != null ? Optional.of(shaderPackText) : Optional.empty();
    }
}

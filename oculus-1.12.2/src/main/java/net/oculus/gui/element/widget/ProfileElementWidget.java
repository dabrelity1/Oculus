package net.oculus.gui.element.widget;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.Profile;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.menu.OptionMenuProfileElement;
import net.oculus.shaderpack.option.values.OptionValues;

/**
 * Widget that mirrors the Iris profile selector, allowing players to cycle
 * through shader profiles inline with other options.
 */
public class ProfileElementWidget extends BaseOptionElementWidget<OptionMenuProfileElement> {
    private static final TextComponentTranslation PROFILE_LABEL = new TextComponentTranslation("options.iris.profile");
    private static final TextComponentTranslation PROFILE_CUSTOM;

    static {
        PROFILE_CUSTOM = new TextComponentTranslation("options.iris.profile.custom");
        Style style = PROFILE_CUSTOM.getStyle();
        if (style == null) {
            style = new Style();
            PROFILE_CUSTOM.setStyle(style);
        }
        style.setColor(TextFormatting.YELLOW);
    }

    private Profile next;
    private Profile previous;
    private ITextComponent profileLabel;

    public ProfileElementWidget(OptionMenuProfileElement element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        super.init(screen, navigation);
        this.setLabel(PROFILE_LABEL);

        ProfileSet profiles = this.element.profiles;
        OptionSet options = this.element.options;
        OptionValues pendingValues = this.element.getPendingOptionValues();

        if (profiles == null || options == null || pendingValues == null) {
            this.profileLabel = PROFILE_CUSTOM.createCopy();
            this.next = null;
            this.previous = null;
            return;
        }

        ProfileSet.ProfileResult result = profiles.scan(options, pendingValues);
        this.next = result.next.orElse(null);
        this.previous = result.previous.orElse(null);

        Optional<Profile> current = result.current;
        if (current.isPresent()) {
            String profileName = current.get().name;
            ITextComponent translated = GuiUtil.translateOrDefault(new TextComponentString(profileName), "profile." + profileName);
            this.profileLabel = translated;
        } else {
            this.profileLabel = PROFILE_CUSTOM.createCopy();
        }
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int minValueSectionWidth = Math.max(80, width - (font.getStringWidth(PROFILE_LABEL.getFormattedText()) + 16));
        this.updateRenderParams(width, minValueSectionWidth);
        this.renderOptionWithValue(x, y, width, height, hovered);
        this.tryRenderTooltip(mouseX, mouseY, hovered);
    }

    @Override
    protected ITextComponent createValueLabel() {
        return this.profileLabel != null ? this.profileLabel.createCopy() : PROFILE_CUSTOM.createCopy();
    }

    @Override
    public String getCommentKey() {
        return "profile.comment";
    }

    @Override
    public boolean applyNextValue() {
        if (this.next != null && this.screen != null) {
            this.screen.applyProfile(this.next);
            return true;
        }
        return false;
    }

    @Override
    public boolean applyPreviousValue() {
        if (this.previous != null && this.screen != null) {
            this.screen.applyProfile(this.previous);
            return true;
        }
        return false;
    }

    @Override
    public boolean applyOriginalValue() {
        return false;
    }

    @Override
    public boolean isValueModified() {
        return false;
    }
}

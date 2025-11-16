package net.oculus.gui.element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.oculus.Oculus;
import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.gui.element.widget.AbstractElementWidget;
import net.oculus.gui.element.IrisElementRow;
import net.oculus.gui.element.widget.OptionMenuConstructor;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.Profile;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.menu.OptionMenuElement;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;

/**
 * 1.12.2-compatible option menu list that mirrors the structure of the Iris
 * ObjectSelectionList implementation using the legacy GuiSlot base class.
 */
public class ShaderPackOptionList extends GuiSlot {
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 16;
    private static final ITextComponent HEADER_BACK_BUTTON_TEXT = buildBackButtonText();
    private static final TextComponentTranslation RESET_HOLD_SHIFT_TOOLTIP = createTooltip("options.iris.reset.tooltip.holdShift", TextFormatting.GOLD);
    private static final TextComponentTranslation RESET_TOOLTIP = createTooltip("options.iris.reset.tooltip", TextFormatting.RED);
    private static final TextComponentTranslation IMPORT_TOOLTIP = createTooltip("options.iris.importSettings.tooltip", TextFormatting.AQUA);
    private static final TextComponentTranslation EXPORT_TOOLTIP = createTooltip("options.iris.exportSettings.tooltip", TextFormatting.GOLD);
    private static final Set<String> PROFILE_WARNING_LOG = new HashSet<>();

    private final ShaderPackScreen screen;
    private NavigationController navigation;
    private final List<Entry> entries = new ArrayList<>();
    private final List<AbstractElementWidget<?>> elementWidgets = new ArrayList<>();

    private ShaderProperties properties = ShaderProperties.empty();
    private MutableOptionValues optionValues = createEmptyOptionValues();
    private OptionMenuContainer container;
    private ProfileSet profileSet = ProfileSet.empty();
    private int slotWidth;
    private long lastClickTime;
    private int lastClickIndex = -1;

    public ShaderPackOptionList(ShaderPackScreen screen, NavigationController navigation, ShaderPack pack, MutableOptionValues values, Minecraft minecraft, int width, int height, int top, int bottom) {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
        this.screen = screen;
        this.navigation = navigation;
        this.navigation.setActiveOptionList(this);
        this.setShowSelectionBox(false);
        this.slotWidth = Math.min(400, width - 12);
        this.left = (width - this.slotWidth) / 2;
        this.right = this.left + this.slotWidth;

        applyShaderPack(pack, values);
        setPackOptions(this.properties);
    }

    public void applyShaderPack(ShaderPack pack, MutableOptionValues values) {
        this.container = pack != null ? pack.getMenuContainer() : OptionMenuContainer.EMPTY;
        this.properties = pack != null ? pack.getProperties() : ShaderProperties.empty();
        this.optionValues = values != null ? values : createEmptyOptionValues();
        this.profileSet = pack != null ? pack.getProfileSet() : ProfileSet.empty();
    }

    public void rebuild() {
        populateEntries();
    }

    public void setPackOptions(ShaderProperties properties) {
        this.properties = properties == null ? ShaderProperties.empty() : properties;
        if (this.optionValues == null) {
            this.optionValues = createEmptyOptionValues();
        }
        populateEntries();
    }

    public void setOptionValues(MutableOptionValues values) {
        this.optionValues = values == null ? createEmptyOptionValues() : values;
        populateEntries();
    }

    private void populateEntries() {
        this.entries.clear();
        this.elementWidgets.clear();
        this.amountScrolled = 0.0F;

        if (this.properties == null) {
            OptionMenuConstructor.constructAndApplyToScreen(this.container, this.screen, this, this.navigation);
            return;
        }

        buildFromProperties(this.properties);
    }

    private void buildFromProperties(ShaderProperties shaderProperties) {
        boolean hasHistory = this.navigation != null && this.navigation.hasHistory();
        ITextComponent heading = GuiUtil.translateOrDefault(new TextComponentString("Shader Options"), "options.iris.shaderPackSettings");
        addHeader(heading, hasHistory);

        Set<String> sliderOptions = new HashSet<>(shaderProperties.getSliderOptions());
        boolean hasOptionWidgets = false;

        List<String> mainOptions = shaderProperties.getMainScreenOptions().orElse(Collections.<String>emptyList());
    List<AbstractElementWidget<?>> mainWidgets = createOptionWidgets(mainOptions, sliderOptions, this.optionValues);
        if (!mainWidgets.isEmpty()) {
            int columns = shaderProperties.getMainScreenColumnCount().orElse(2);
            addWidgets(Math.max(1, columns), mainWidgets);
            hasOptionWidgets = true;
        }

        for (Map.Entry<String, List<String>> entry : shaderProperties.getSubScreenOptions().entrySet()) {
            List<AbstractElementWidget<?>> widgets = createOptionWidgets(entry.getValue(), sliderOptions, this.optionValues);
            if (widgets.isEmpty()) {
                continue;
            }

            this.entries.add(new SectionHeaderEntry(entry.getKey()));
            Integer columnOverride = shaderProperties.getSubScreenColumnCount().get(entry.getKey());
            int columns = columnOverride != null ? columnOverride : shaderProperties.getMainScreenColumnCount().orElse(2);
            addWidgets(Math.max(1, columns), widgets);
            hasOptionWidgets = true;
        }

        if (!hasOptionWidgets) {
            ITextComponent message = GuiUtil.translateOrDefault(new TextComponentString("No shader options available"), "options.iris.noShaderOptions");
            this.entries.add(new MessageEntry(message));
        }

        if (!shaderProperties.getProfiles().isEmpty()) {
            for (Map.Entry<String, List<String>> profile : shaderProperties.getProfiles().entrySet()) {
                Profile resolved = this.profileSet.get(profile.getKey()).orElse(null);
                if (resolved == null) {
                    logMissingProfile(profile.getKey());
                    continue;
                }

                ITextComponent profileHeading = new TextComponentString(formatDisplayName(profile.getKey()));
                addHeader(profileHeading, false, false);
                this.entries.add(new ProfileEntry(resolved, profile.getValue()));
            }
        }
    }

    private List<AbstractElementWidget<?>> createOptionWidgets(List<String> optionNames, Set<String> sliderOptions, MutableOptionValues values) {
        List<AbstractElementWidget<?>> widgets = new ArrayList<>();

        if (optionNames == null) {
            return widgets;
        }

        for (String rawName : optionNames) {
            if (rawName == null) {
                continue;
            }

            String name = rawName.trim();

            if (name.isEmpty()) {
                continue;
            }

            if (sliderOptions.contains(name)) {
                widgets.add(new SliderOptionWidget(name, formatDisplayName(name), values));
            } else {
                widgets.add(new ToggleOptionWidget(name, formatDisplayName(name), values));
            }
        }

        return widgets;
    }

    private static String formatDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String sanitized = name.replace('_', ' ').replace('.', ' ');
        String[] parts = sanitized.split("\\s+");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.length() > 0 ? builder.toString() : name;
    }

    private static MutableOptionValues createEmptyOptionValues() {
        return new MutableOptionValues(OptionSet.builder().build());
    }

    private static void logMissingProfile(String profileName) {
        if (profileName == null) {
            return;
        }
        if (PROFILE_WARNING_LOG.add(profileName)) {
            Oculus.LOGGER.warn("Skipping shader profile '{}' because it could not be parsed", profileName);
        }
    }

    public void refresh() {
        for (AbstractElementWidget<?> widget : this.elementWidgets) {
            widget.init(this.screen, this.navigation);
        }
    }

    public void layout(int left, int width) {
        this.left = left;
        this.right = left + width;
        this.slotWidth = width;
    }

    public void updateNavigation(NavigationController navigation) {
        this.navigation = navigation;
        this.navigation.setActiveOptionList(this);
    }

    public void resize(int width, int height, int top, int bottom) {
        this.width = width;
        this.height = height;
        this.top = top;
        this.bottom = bottom;
    }

    public NavigationController getNavigation() {
        return this.navigation;
    }

    public void addHeader(ITextComponent text, boolean backButton) {
        addHeader(text, backButton, true);
    }

    private void addHeader(ITextComponent text, boolean backButton, boolean showUtilityButtons) {
        this.entries.add(new HeaderEntry(this.screen, this.navigation, text, backButton, showUtilityButtons));
    }

    public void addWidgets(int columns, List<AbstractElementWidget<?>> elements) {
        this.elementWidgets.addAll(elements);

        List<AbstractElementWidget<?>> row = new ArrayList<>();
        for (AbstractElementWidget<?> element : elements) {
            row.add(element);
            if (row.size() >= columns) {
                this.entries.add(new ElementRowEntry(this.screen, new ArrayList<>(row)));
                row.clear();
            }
        }

        if (!row.isEmpty()) {
            while (row.size() < columns) {
                row.add(AbstractElementWidget.EMPTY);
            }
            this.entries.add(new ElementRowEntry(this.screen, new ArrayList<>(row)));
        }
    }

    @Override
    protected int getSize() {
        return entries.size();
    }

    @Override
    protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
        if (slotIndex < 0 || slotIndex >= entries.size()) {
            return;
        }

        entries.get(slotIndex).click(slotIndex, mouseX, mouseY, 0, isDoubleClick);
    }

    @Override
    protected boolean isSelected(int slotIndex) {
        return false;
    }

    @Override
    protected void drawBackground() {
        // Background handled by the parent screen.
    }

    @Override
    protected void drawSlot(int slotIndex, int xPos, int yPos, int heightIn, int mouseX, int mouseY, float partialTicks) {
        if (slotIndex < 0 || slotIndex >= entries.size()) {
            return;
        }

        entries.get(slotIndex).draw(slotIndex, xPos, yPos, heightIn, mouseX, mouseY, partialTicks);
    }

    @Override
    public int getListWidth() {
        return Math.max(0, this.slotWidth);
    }

    @Override
    protected int getScrollBarX() {
        return this.left + getListWidth() + 6;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isWithinBounds(mouseX, mouseY)) {
            return false;
        }

        int index = getEntryIndexAt(mouseX, mouseY);
        if (index < 0 || index >= entries.size()) {
            return false;
        }

        long currentTime = Minecraft.getSystemTime();
        boolean isDoubleClick = index == lastClickIndex && currentTime - lastClickTime < 250L;
        lastClickIndex = index;
        lastClickTime = currentTime;

        elementClicked(index, isDoubleClick, mouseX, mouseY);
        return true;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).release(i, mouseX, mouseY, button);
        }
    }

    private boolean isWithinBounds(int mouseX, int mouseY) {
        return mouseX >= this.left && mouseX <= this.left + getListWidth()
            && mouseY >= this.top && mouseY <= this.bottom;
    }

    private int getEntryIndexAt(int mouseX, int mouseY) {
        int listLeft = this.left;
        int listRight = listLeft + getListWidth();
        if (mouseX < listLeft || mouseX > listRight) {
            return -1;
        }

        int offsetY = mouseY - this.top - this.headerPadding + (int) this.amountScrolled - 4;
        if (offsetY < 0) {
            return -1;
        }

        int index = offsetY / this.slotHeight;
        return index >= 0 && index < entries.size() ? index : -1;
    }

    private static ITextComponent buildBackButtonText() {
        TextComponentString prefix = new TextComponentString("< ");
        TextComponentTranslation label = new TextComponentTranslation("options.iris.back");
        label.getStyle().setItalic(true).setColor(TextFormatting.WHITE);
        return prefix.appendSibling(label);
    }

    private static TextComponentTranslation createResetText(TextFormatting color) {
        TextComponentTranslation translation = new TextComponentTranslation("options.iris.reset");
        translation.getStyle().setColor(color);
        return translation;
    }

    private static TextComponentTranslation createTooltip(String key, TextFormatting color) {
        TextComponentTranslation component = new TextComponentTranslation(key);
        component.getStyle().setColor(color);
        return component;
    }

    public void keyTyped(char typedChar, int keyCode) {
        // Placeholder for future widgets needing keyboard input.
    }

    private interface Entry {
        void draw(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks);

        void click(int index, int mouseX, int mouseY, int button, boolean isDoubleClick);

        void release(int index, int mouseX, int mouseY, int button);
    }

    private class MessageEntry implements Entry {
        private final ITextComponent text;

        private MessageEntry(ITextComponent text) {
            this.text = text;
        }

        @Override
        public void draw(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = ShaderPackOptionList.this.mc.fontRenderer;
            String value = text.getFormattedText();
            int textX = x + (ShaderPackOptionList.this.getListWidth() - font.getStringWidth(value)) / 2;
            int textY = y + (height - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(value, textX, textY, 0xC2C2C2);
        }

        @Override
        public void click(int index, int mouseX, int mouseY, int button, boolean isDoubleClick) {
        }

        @Override
        public void release(int index, int mouseX, int mouseY, int button) {
        }
    }

    private class SectionHeaderEntry implements Entry {
        private final String title;

        private SectionHeaderEntry(String title) {
            this.title = title;
        }

        @Override
        public void draw(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            String display = formatDisplayName(this.title);
            font.drawStringWithShadow(display, x + 4, y + (height - font.FONT_HEIGHT) / 2, 0xFFFFFF);
            Gui.drawRect(x - 3, y + height - 2, x + ShaderPackOptionList.this.getListWidth(), y + height - 1, 0x55BEBEBE);
        }

        @Override
        public void click(int index, int mouseX, int mouseY, int button, boolean isDoubleClick) {
        }

        @Override
        public void release(int index, int mouseX, int mouseY, int button) {
        }
    }

    private class ProfileEntry implements Entry {
        private final Profile profile;
        private final List<String> assignments;
        private final IrisElementRow buttonRow;
        private final IrisElementRow.TextButtonElement applyButton;
        private final ITextComponent applyTooltip;

        private ProfileEntry(Profile profile, List<String> assignments) {
            this.profile = profile;
            this.assignments = assignments == null ? Collections.<String>emptyList() : assignments;
            ITextComponent buttonLabel = GuiUtil.translateOrDefault(new TextComponentString("Apply"), "options.iris.profile.apply");
            this.applyTooltip = GuiUtil.translateOrDefault(new TextComponentString("Apply profile"), "options.iris.profile.apply.tooltip");
            this.applyButton = new IrisElementRow.TextButtonElement(buttonLabel, this::applyButtonClicked);
            this.buttonRow = new IrisElementRow().add(this.applyButton, 64);
            this.applyButton.disabled = this.profile == null;
        }

        @Override
        public void draw(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = ShaderPackOptionList.this.mc.fontRenderer;
            String label = "Profile: " + formatDisplayName(this.profile != null ? this.profile.name : "");
            boolean isActive = this.profile != null
                && ShaderPackOptionList.this.optionValues != null
                && ShaderPackOptionList.this.optionValues.getOptionSet() != null
                && this.profile.matches(ShaderPackOptionList.this.optionValues.getOptionSet(), ShaderPackOptionList.this.optionValues);

            if (isActive) {
                String activeLabel = I18n.hasKey("options.iris.profile.active")
                    ? I18n.format("options.iris.profile.active")
                    : "Active";
                label = label + " (" + activeLabel + ")";
            }

            int labelColor = isActive ? 0xFFFF55 : 0xFFFFFF;
            font.drawStringWithShadow(label, x + 4, y + 4, labelColor);

            if (!this.assignments.isEmpty()) {
                String joined = String.join(", ", this.assignments);
                joined = GuiUtil.shortenText(font, joined, ShaderPackOptionList.this.getListWidth() - 80);
                font.drawStringWithShadow(joined, x + 4, y + 14, 0xA0A0A0);
            }

            boolean rowHovered = mouseX >= x && mouseX <= x + ShaderPackOptionList.this.getListWidth()
                && mouseY >= y && mouseY <= y + height;

            this.buttonRow.renderRightAligned(x + ShaderPackOptionList.this.getListWidth() - 3, y + 2, BUTTON_HEIGHT, mouseX, mouseY, partialTicks, rowHovered);

            if (this.applyButton.isHovered()) {
                queueTooltip(font, this.applyTooltip, mouseX, mouseY);
            }
        }

        private boolean applyButtonClicked(IrisElementRow.TextButtonElement button) {
            if (this.profile == null) {
                return false;
            }

            GuiUtil.playButtonClickSound();
            screen.applyProfile(this.profile);
            return true;
        }

        @Override
        public void click(int index, int mouseX, int mouseY, int button, boolean isDoubleClick) {
            this.buttonRow.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void release(int index, int mouseX, int mouseY, int button) {
            this.buttonRow.mouseReleased(mouseX, mouseY, button);
        }

        private void queueTooltip(FontRenderer font, ITextComponent tooltip, int mouseX, int mouseY) {
            final String text = tooltip.getFormattedText();
            final int tooltipX = mouseX - (font.getStringWidth(text) + 10);
            final int tooltipY = mouseY - 16;
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, text, tooltipX, tooltipY));
        }
    }

    private class HeaderEntry implements Entry {
        private static final int MIN_SIDE_BUTTON_WIDTH = 42;

        private final ShaderPackScreen screen;
        private final NavigationController navigation;
        private final ITextComponent title;
        private final boolean showUtilityButtons;
        private final IrisElementRow utilityButtons;
        private final IrisElementRow.TextButtonElement resetButton;
        private final IrisElementRow.IconButtonElement importButton;
        private final IrisElementRow.IconButtonElement exportButton;
        private final IrisElementRow backButtonRow;

        private HeaderEntry(ShaderPackScreen screen, NavigationController navigation, ITextComponent title, boolean hasBackButton, boolean showUtilityButtons) {
            this.screen = screen;
            this.navigation = navigation;
            this.title = title;
            this.showUtilityButtons = showUtilityButtons;

            FontRenderer font = Minecraft.getMinecraft().fontRenderer;

            if (hasBackButton) {
                int backWidth = Math.max(MIN_SIDE_BUTTON_WIDTH, font.getStringWidth(HEADER_BACK_BUTTON_TEXT.getFormattedText()) + 8);
                this.backButtonRow = new IrisElementRow().add(new IrisElementRow.TextButtonElement(HEADER_BACK_BUTTON_TEXT, this::backButtonClicked), backWidth);
            } else {
                this.backButtonRow = null;
            }

            if (showUtilityButtons) {
                ITextComponent initialResetText = createResetText(TextFormatting.GRAY);
                int resetWidth = Math.max(MIN_SIDE_BUTTON_WIDTH, font.getStringWidth(initialResetText.getFormattedText()) + 8);

                this.resetButton = new IrisElementRow.TextButtonElement(initialResetText, this::resetButtonClicked);
                this.importButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.IMPORT, GuiUtil.Icon.IMPORT_COLORED, this::importSettingsButtonClicked);
                this.exportButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.EXPORT, GuiUtil.Icon.EXPORT_COLORED, this::exportSettingsButtonClicked);
                this.utilityButtons = new IrisElementRow()
                    .add(this.importButton, 15)
                    .add(this.exportButton, 15)
                    .add(this.resetButton, resetWidth);
            } else {
                this.resetButton = null;
                this.importButton = null;
                this.exportButton = null;
                this.utilityButtons = null;
            }
        }

        @Override
        public void draw(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
            int bottom = y + height;
            Gui.drawRect(x - 3, bottom - 2, x + ShaderPackOptionList.this.getListWidth(), bottom - 1, 0x66BEBEBE);

            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            String titleText = this.title.getFormattedText();
            font.drawStringWithShadow(titleText, x + ShaderPackOptionList.this.getListWidth() / 2 - font.getStringWidth(titleText) / 2, y + 5, 0xFFFFFF);

            if (backButtonRow != null) {
                GuiUtil.bindIrisWidgetsTexture();
                backButtonRow.render(x, y + 2, BUTTON_HEIGHT, mouseX, mouseY, partialTicks, true);
            }

            if (showUtilityButtons && this.utilityButtons != null) {
                boolean shiftDown = GuiScreen.isShiftKeyDown();
                this.resetButton.disabled = !shiftDown;
                this.resetButton.text = createResetText(shiftDown ? TextFormatting.YELLOW : TextFormatting.GRAY);

                GuiUtil.bindIrisWidgetsTexture();
                this.utilityButtons.renderRightAligned(x + ShaderPackOptionList.this.getListWidth() - 3, y + 2, BUTTON_HEIGHT, mouseX, mouseY, partialTicks, true);

                if (this.resetButton.isHovered()) {
                    queueTooltip(mouseX, mouseY, font, shiftDown ? RESET_TOOLTIP : RESET_HOLD_SHIFT_TOOLTIP);
                }
                if (this.importButton.isHovered()) {
                    queueTooltip(mouseX, mouseY, font, IMPORT_TOOLTIP);
                }
                if (this.exportButton.isHovered()) {
                    queueTooltip(mouseX, mouseY, font, EXPORT_TOOLTIP);
                }
            }
        }

        private boolean backButtonClicked(IrisElementRow.TextButtonElement button) {
            navigation.back();
            GuiUtil.playButtonClickSound();
            return true;
        }

        private boolean resetButtonClicked(IrisElementRow.TextButtonElement button) {
            if (!GuiScreen.isShiftKeyDown()) {
                return false;
            }

            GuiUtil.playButtonClickSound();
            screen.resetCurrentPackOptions();
            return true;
        }

        private boolean importSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
            GuiUtil.playButtonClickSound();
            screen.beginImportSettingsFlow();
            return true;
        }

        private boolean exportSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
            GuiUtil.playButtonClickSound();
            screen.beginExportSettingsFlow();
            return true;
        }

        @Override
        public void click(int index, int mouseX, int mouseY, int button, boolean isDoubleClick) {
            if (backButtonRow != null) {
                backButtonRow.mouseClicked(mouseX, mouseY, button);
            }
            if (showUtilityButtons && utilityButtons != null) {
                utilityButtons.mouseClicked(mouseX, mouseY, button);
            }
        }

        @Override
        public void release(int index, int mouseX, int mouseY, int button) {
            if (backButtonRow != null) {
                backButtonRow.mouseReleased(mouseX, mouseY, button);
            }
            if (showUtilityButtons && utilityButtons != null) {
                utilityButtons.mouseReleased(mouseX, mouseY, button);
            }
        }

        private void queueTooltip(int mouseX, int mouseY, FontRenderer font, ITextComponent tooltip) {
            final String text = tooltip.getFormattedText();
            final int tooltipX = mouseX - (font.getStringWidth(text) + 10);
            final int tooltipY = mouseY - 16;
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, text, tooltipX, tooltipY));
        }

        private ITextComponent colouredFallback(String key, String fallback, TextFormatting color) {
            ITextComponent component = GuiUtil.translateOrDefault(new TextComponentString(fallback), key);
            component.getStyle().setColor(color);
            return component;
        }
    }

    private class ElementRowEntry implements Entry {
        private final List<AbstractElementWidget<?>> widgets;
        private final ShaderPackScreen screen;

        private int cachedWidth;
        private int cachedPosX;

        private ElementRowEntry(ShaderPackScreen screen, List<AbstractElementWidget<?>> widgets) {
            this.screen = screen;
            this.widgets = widgets;
        }

        @Override
        public void draw(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
            this.cachedWidth = ShaderPackOptionList.this.getListWidth();
            this.cachedPosX = x;

            int totalWithoutMargins = this.cachedWidth - (2 * (widgets.size() - 1)) - 3;
            float singleWidth = (float) totalWithoutMargins / widgets.size();

            for (int i = 0; i < widgets.size(); i++) {
                AbstractElementWidget<?> widget = widgets.get(i);
                int widgetX = x + (int) ((singleWidth + 2) * i);
                int widgetWidth = (int) singleWidth;
                boolean hovered = isMouseOverWidget(i, mouseX);
                widget.render(widgetX, y, widgetWidth, height + 2, mouseX, mouseY, partialTicks, hovered);
                screen.setElementHoveredStatus(widget, hovered);
            }
        }

        private boolean isMouseOverWidget(int index, int mouseX) {
            float position = (float) Math.max(0, Math.min(mouseX - cachedPosX, cachedWidth)) / cachedWidth;
            int hoveredIndex = Math.min(widgets.size() - 1, Math.max(0, (int) Math.floor(widgets.size() * position)));
            return hoveredIndex == index;
        }

        private int getHoveredIndex(int mouseX) {
            float position = (float) Math.max(0, Math.min(mouseX - cachedPosX, cachedWidth)) / cachedWidth;
            return Math.min(widgets.size() - 1, Math.max(0, (int) Math.floor(widgets.size() * position)));
        }

        @Override
        public void click(int index, int mouseX, int mouseY, int button, boolean isDoubleClick) {
            widgets.get(getHoveredIndex(mouseX)).mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void release(int index, int mouseX, int mouseY, int button) {
            widgets.get(getHoveredIndex(mouseX)).mouseReleased(mouseX, mouseY, button);
        }
    }

    private static class ToggleOptionWidget extends AbstractElementWidget<OptionMenuElement> {
        private final String optionName;
        private final String displayName;
        private final MutableOptionValues values;
        private boolean value;
        private int lastX;
        private int lastWidth;
        private ShaderPackScreen screen;

        private ToggleOptionWidget(String optionName, String displayName, MutableOptionValues values) {
            super(OptionMenuElement.EMPTY);
            this.optionName = optionName;
            this.displayName = displayName;
            this.values = values;
        }

        @Override
        public void init(ShaderPackScreen screen, NavigationController navigation) {
            this.screen = screen;
            pullFromModel();
        }

        private void pullFromModel() {
            if (values == null) {
                this.value = false;
                return;
            }

            this.value = values.getBooleanValueOrDefault(optionName);
        }

        private void pushToModel() {
            if (values == null) {
                return;
            }

            values.setBooleanValue(optionName, this.value);
        }

        @Override
        public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            this.lastX = x;
            this.lastWidth = width;

            pullFromModel();

            GuiUtil.drawButton(x, y, width, height, hovered, false);

            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            String label = (displayName == null || displayName.isEmpty() ? optionName : displayName) + ": " + (value ? "ON" : "OFF");
            label = GuiUtil.shortenText(font, label, width - 10);
            font.drawStringWithShadow(label, x + 5, y + (height - font.FONT_HEIGHT) / 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) {
                return false;
            }

            if (mouseX < this.lastX || mouseX > this.lastX + this.lastWidth) {
                return false;
            }

            this.value = !this.value;
            pushToModel();
            notifyChange();
            GuiUtil.playButtonClickSound();
            return true;
        }

        private void notifyChange() {
            if (this.screen != null) {
                this.screen.markPendingChanges();
            }
        }
    }

    private static class SliderOptionWidget extends AbstractElementWidget<OptionMenuElement> {
        private final String optionName;
        private final String displayName;
        private final MutableOptionValues values;
        private float progress = DEFAULT_VALUE;
        private boolean dragging;
        private int lastX;
        private int lastWidth;
        private int lastY;
        private int lastHeight;
    private ShaderPackScreen screen;

        private static final float DEFAULT_VALUE = 0.5F;

        private SliderOptionWidget(String optionName, String displayName, MutableOptionValues values) {
            super(OptionMenuElement.EMPTY);
            this.optionName = optionName;
            this.displayName = displayName;
            this.values = values;
        }

        @Override
        public void init(ShaderPackScreen screen, NavigationController navigation) {
            this.screen = screen;
            pullFromModel();
        }

        private void pullFromModel() {
            if (values == null) {
                this.progress = DEFAULT_VALUE;
                return;
            }

            this.progress = clamp(values.getFloatValueOrDefault(optionName, DEFAULT_VALUE));
        }

        private void pushToModel() {
            if (values == null) {
                return;
            }

            values.setFloatValue(optionName, this.progress);
        }

        @Override
        public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            this.lastX = x;
            this.lastWidth = width;
            this.lastY = y;
            this.lastHeight = height;

            if (this.dragging) {
                updateProgress(mouseX);
                pushToModel();
            } else {
                pullFromModel();
            }

            GuiUtil.drawButton(x, y, width, height, hovered || dragging, false);

            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            String label = displayName == null || displayName.isEmpty() ? optionName : displayName;
            String valueText = label + ": " + Math.round(progress * 100) + "%";
            valueText = GuiUtil.shortenText(font, valueText, width - 10);
            font.drawStringWithShadow(valueText, x + 5, y + 5, 0xFFFFFF);

            int trackLeft = x + 6;
            int trackRight = x + width - 6;
            int trackY = y + height - 8;
            if (trackRight > trackLeft) {
                Gui.drawRect(trackLeft, trackY, trackRight, trackY + 1, 0xFF3B3B3B);
                int knobX = trackLeft + Math.round((trackRight - trackLeft) * progress);
                Gui.drawRect(knobX - 2, trackY - 4, knobX + 2, trackY + 5, 0xFFAAAAAA);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0 || !isInside(mouseX, mouseY)) {
                return false;
            }

            updateProgress(mouseX);
            pushToModel();
            notifyChange();
            this.dragging = true;
            GuiUtil.playButtonClickSound();
            return true;
        }

        @Override
        public boolean mouseReleased(int mouseX, int mouseY, int button) {
            if (button != 0 || !this.dragging) {
                return false;
            }

            updateProgress(mouseX);
            pushToModel();
            notifyChange();
            this.dragging = false;
            return true;
        }

        private boolean isInside(int mouseX, int mouseY) {
            return mouseX >= this.lastX && mouseX <= this.lastX + this.lastWidth
                && mouseY >= this.lastY && mouseY <= this.lastY + this.lastHeight;
        }

        private void updateProgress(int mouseX) {
            int trackLeft = this.lastX + 6;
            int trackRight = this.lastX + this.lastWidth - 6;

            if (trackRight <= trackLeft) {
                this.progress = 0.0F;
                return;
            }

            float relative = (mouseX - trackLeft) / (float) (trackRight - trackLeft);
            this.progress = clamp(relative);
        }

        private float clamp(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }

        private void notifyChange() {
            if (this.screen != null) {
                this.screen.markPendingChanges();
            }
        }
    }
}

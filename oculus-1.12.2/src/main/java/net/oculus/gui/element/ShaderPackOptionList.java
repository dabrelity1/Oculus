package net.oculus.gui.element;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.gui.element.widget.AbstractElementWidget;
import net.oculus.gui.element.IrisElementRow;
import net.oculus.gui.element.widget.OptionMenuConstructor;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.OptionSet;
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

    private final ShaderPackScreen screen;
    private NavigationController navigation;
    private final List<Entry> entries = new ArrayList<>();
    private final List<AbstractElementWidget<?>> elementWidgets = new ArrayList<>();

    private ShaderProperties properties = ShaderProperties.empty();
    private MutableOptionValues optionValues = createEmptyOptionValues();
    private OptionMenuContainer container;
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

        OptionMenuConstructor.constructAndApplyToScreen(this.container, this.screen, this, this.navigation);
    }

    private static MutableOptionValues createEmptyOptionValues() {
        return new MutableOptionValues(OptionSet.builder().build());
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
        // No current shader option widgets consume typed keyboard input.
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

}

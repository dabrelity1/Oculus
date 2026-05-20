package net.oculus.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import org.lwjgl.input.Keyboard;

import net.oculus.Oculus;
import net.oculus.gui.GuiUtil.Icon;
import net.oculus.shaderpack.discovery.ShaderpackDirectoryManager;

/**
 * 1.12.2 implementation of the shader pack selection list using {@link GuiSlot}.
 */
public class ShaderPackSelectionList extends GuiSlot {
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 2;
    private static final int REFRESH_BUTTON_WIDTH = 18;

    private final ShaderPackScreen screen;
    private final Minecraft minecraft;
    private final List<Entry> entries = new ArrayList<>();
    private final TopButtonRowEntry topButtonRow;
    private ShadowDistanceEntry shadowDistanceEntry;

    private ShaderPackEntry appliedEntry;
    private String appliedPackName;
    private int selectedIndex = -1;
    private int slotWidth;
    private long lastClickTime;
    private int lastClickIndex = -1;

    public ShaderPackSelectionList(ShaderPackScreen screen, Minecraft minecraft, int width, int height, int top, int bottom) {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
        this.screen = screen;
        this.minecraft = minecraft;
        this.topButtonRow = new TopButtonRowEntry();
        this.setShowSelectionBox(false);
        this.slotWidth = Math.min(308, width - 50);
        int centeredLeft = (width - this.slotWidth) / 2;
        layout(centeredLeft, this.slotWidth);
        refresh();
    }

    public void layout(int left, int width) {
        this.left = left;
        this.right = left + width;
        this.slotWidth = width;
        this.width = width;
    }

    public void resize(int width, int height, int top, int bottom) {
        this.width = width;
        this.height = height;
        this.top = top;
        this.bottom = bottom;
    }

    public void refresh() {
        String previouslySelected = screen.getSelectedPackName();
        ShaderPackEntry selected = getSelectedEntry();
        if (selected != null) {
            previouslySelected = selected.packName;
        }

        String appliedPackName = screen.getAppliedPackName();
        this.appliedEntry = null;
        this.appliedPackName = null;

        entries.clear();
        selectedIndex = -1;
        entries.add(topButtonRow);
        entries.add(new ColorSpaceEntry());
        this.shadowDistanceEntry = new ShadowDistanceEntry();
        entries.add(this.shadowDistanceEntry);

        Collection<String> names;
        try {
            names = ShaderpackDirectoryManager.findShaderPacks();
        } catch (Throwable throwable) {
            Oculus.LOGGER.error("Error reading shaderpacks directory", throwable);
            addErrorMessage();
            return;
        }

        topButtonRow.allowEnableShadersButton = !names.isEmpty();

        for (String name : names) {
            ShaderPackEntry entry = new ShaderPackEntry(name);
            entries.add(entry);
            if (appliedPackName != null && appliedPackName.equals(name)) {
                setApplied(entry);
            }
        }

        entries.add(new LabelEntry(I18n.format("pack.iris.list.label")));

        if (this.appliedEntry == null) {
            this.appliedPackName = appliedPackName;
        }

        if (previouslySelected != null) {
            select(previouslySelected);
        }
    }

    private void addErrorMessage() {
        entries.add(new LabelEntry(""));
        entries.add(new LabelEntry(TextFormatting.RED + "There was an error reading your shaderpacks directory"));
        entries.add(new LabelEntry("Check your logs for more information."));
        entries.add(new LabelEntry("Please file an issue report including a log file."));
        entries.add(new LabelEntry("Ensure folder permissions are correct."));
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

        Entry entry = entries.get(slotIndex);
        entry.click(slotIndex, mouseX, mouseY, isDoubleClick);
    }

    @Override
    protected boolean isSelected(int slotIndex) {
        return slotIndex == selectedIndex;
    }

    public ShaderPackEntry getSelectedEntry() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            Entry entry = entries.get(selectedIndex);
            if (entry instanceof ShaderPackEntry) {
                return (ShaderPackEntry) entry;
            }
        }
        return null;
    }

    public void select(String name) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry instanceof ShaderPackEntry && ((ShaderPackEntry) entry).packName.equals(name)) {
                selectEntry((ShaderPackEntry) entry);
                selectedIndex = i;
                break;
            }
        }
    }

    @Override
    protected void drawSlot(int slotIndex, int xPos, int yPos, int heightIn, int mouseX, int mouseY, float partialTicks) {
        if (slotIndex < 0 || slotIndex >= entries.size()) {
            return;
        }

        Entry entry = entries.get(slotIndex);
        entry.draw(slotIndex, xPos, yPos, heightIn, mouseX, mouseY, partialTicks);
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
        if (this.shadowDistanceEntry != null) {
            this.shadowDistanceEntry.mouseReleased();
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

    @Override
    protected int getScrollBarX() {
        return this.left + getListWidth() + 4;
    }

    @Override
    public int getListWidth() {
        return Math.max(0, this.slotWidth);
    }

    @Override
    protected void drawBackground() {
        // Background handled by parent screen.
    }

    public void setApplied(ShaderPackEntry entry) {
        this.appliedEntry = entry;
        this.appliedPackName = entry != null ? entry.packName : null;
    }

    public void markAppliedPack(String packName) {
        this.appliedEntry = null;
        this.appliedPackName = packName;

        if (packName == null) {
            return;
        }

        for (Entry entry : entries) {
            if (entry instanceof ShaderPackEntry) {
                ShaderPackEntry shaderEntry = (ShaderPackEntry) entry;
                if (shaderEntry.packName.equals(packName)) {
                    setApplied(shaderEntry);
                    break;
                }
            }
        }
    }

    public ShaderPackEntry getApplied() {
        return this.appliedEntry;
    }

    public TopButtonRowEntry getTopButtonRow() {
        return this.topButtonRow;
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            moveSelection(-1);
        } else if (keyCode == Keyboard.KEY_DOWN) {
            moveSelection(1);
        }
    }

    private void selectEntry(ShaderPackEntry entry) {
        selectedIndex = entries.indexOf(entry);
    }

    private int contentWidth() {
        return getListWidth();
    }

    private void moveSelection(int direction) {
        if (entries.isEmpty()) {
            return;
        }

        int startIndex = selectedIndex;
        if (startIndex < 0) {
            startIndex = direction > 0 ? -1 : entries.size();
        }

        int index = startIndex;
        while (true) {
            index += direction;
            if (index < 0 || index >= entries.size()) {
                return;
            }

            Entry entry = entries.get(index);
            if (entry instanceof ShaderPackEntry) {
                if (!topButtonRow.shadersEnabled) {
                    topButtonRow.setShadersEnabled(true);
                }
                selectEntry((ShaderPackEntry) entry);
                screen.onShaderPackSelected(((ShaderPackEntry) entry).packName);
                return;
            }
        }
    }

    private interface Entry {
        void draw(int slotIndex, int xPos, int yPos, int slotHeight, int mouseX, int mouseY, float partialTicks);

        void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick);
    }

    private class ShaderPackEntry implements Entry {
        private final String packName;

        private ShaderPackEntry(String packName) {
            this.packName = packName;
        }

        @Override
        public void draw(int slotIndex, int xPos, int yPos, int slotHeight, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = minecraft.fontRenderer;
            String name = GuiUtil.shortenText(font, packName, contentWidth() - 8);

            boolean hovered = mouseX >= xPos && mouseX <= xPos + contentWidth() && mouseY >= yPos && mouseY <= yPos + slotHeight;
            boolean applied = appliedEntry == this;
            boolean shadersEnabled = topButtonRow.shadersEnabled;

            int color = 0xFFFFFF;
            if (hovered) {
                name = TextFormatting.BOLD + name + TextFormatting.RESET;
            }
            if (shadersEnabled && applied) {
                color = 0xFFF263;
            }
            if (!shadersEnabled && !hovered) {
                color = 0xA2A2A2;
            }

            int textX = xPos + (contentWidth() - font.getStringWidth(name)) / 2;
            int textY = yPos + (slotHeight - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(name, textX, textY, color);
        }

        @Override
        public void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick) {
            if (!topButtonRow.shadersEnabled) {
                topButtonRow.setShadersEnabled(true);
            }

            if (selectedIndex != slotIndex) {
                selectEntry(this);
                screen.onShaderPackSelected(this.packName);
            }
        }
    }

    private class LabelEntry implements Entry {
        private final String text;

        private LabelEntry(String text) {
            this.text = text;
        }

        @Override
        public void draw(int slotIndex, int xPos, int yPos, int slotHeight, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = minecraft.fontRenderer;
            int textX = xPos + (contentWidth() - font.getStringWidth(text)) / 2;
            int textY = yPos + (slotHeight - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(text, textX, textY, 0xC2C2C2);
        }

        @Override
        public void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick) {
            // No-op
        }
    }

    private class ColorSpaceEntry implements Entry {
        private int lastButtonX;
        private int lastButtonY;
        private int lastButtonWidth;

        @Override
        public void draw(int slotIndex, int xPos, int yPos, int slotHeight, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = minecraft.fontRenderer;
            int buttonX = xPos + BUTTON_MARGIN;
            int buttonY = yPos + BUTTON_MARGIN;
            int buttonWidth = Math.max(1, contentWidth() - BUTTON_MARGIN * 2);

            this.lastButtonX = buttonX;
            this.lastButtonY = buttonY;
            this.lastButtonWidth = buttonWidth;

            boolean disabled = !screen.isColorSpaceControlAvailable();
            boolean hovered = !disabled && isWithin(mouseX, mouseY, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT);

            GuiUtil.bindIrisWidgetsTexture();
            GuiUtil.drawButton(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, hovered, disabled);

            String label = GuiUtil.shortenText(font, screen.getColorSpaceButtonLabel(), buttonWidth - 8);
            int textX = buttonX + (buttonWidth - font.getStringWidth(label)) / 2;
            int textY = buttonY + (BUTTON_HEIGHT - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(label, textX, textY, disabled ? 0xA2A2A2 : 0xFFFFFF);

            if (hovered) {
                final String tooltip = screen.getColorSpaceTooltip();
                final int tooltipX = mouseX - 8 - font.getStringWidth(tooltip);
                final int tooltipY = mouseY - 16;
                ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, tooltip, tooltipX, tooltipY));
            }
        }

        @Override
        public void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick) {
            if (!screen.isColorSpaceControlAvailable()) {
                return;
            }

            if (isWithin(mouseX, mouseY, lastButtonX, lastButtonY, lastButtonWidth, BUTTON_HEIGHT)) {
                GuiUtil.playButtonClickSound();
                screen.cycleColorSpace();
            }
        }

        private boolean isWithin(int mouseX, int mouseY, int x, int y, int w, int h) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    private class ShadowDistanceEntry implements Entry {
        private static final int SLIDER_KNOB_WIDTH = 6;

        private int lastSliderX;
        private int lastSliderY;
        private int lastSliderWidth;
        private boolean dragging;

        @Override
        public void draw(int slotIndex, int xPos, int yPos, int slotHeight, int mouseX, int mouseY, float partialTicks) {
            FontRenderer font = minecraft.fontRenderer;
            int buttonX = xPos + BUTTON_MARGIN;
            int buttonY = yPos + BUTTON_MARGIN;
            int buttonWidth = Math.max(1, contentWidth() - BUTTON_MARGIN * 2);

            this.lastSliderX = buttonX + 4;
            this.lastSliderY = buttonY + 2;
            this.lastSliderWidth = Math.max(1, buttonWidth - 8);

            boolean disabled = !screen.isShadowDistanceControlAvailable();
            boolean rowHovered = isWithin(mouseX, mouseY, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT);
            boolean hovered = !disabled && rowHovered;
            if (this.dragging && disabled) {
                this.dragging = false;
            } else if (this.dragging) {
                updateFromMouse(mouseX);
            }

            GuiUtil.bindIrisWidgetsTexture();
            GuiUtil.drawButton(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, hovered, disabled);
            GuiUtil.drawButton(this.lastSliderX, this.lastSliderY, this.lastSliderWidth, BUTTON_HEIGHT - 4, false, true);

            int knobTravel = Math.max(0, this.lastSliderWidth - SLIDER_KNOB_WIDTH);
            int knobX = this.lastSliderX + Math.round(screen.getShadowDistanceSliderFraction() * knobTravel);
            GuiUtil.drawButton(knobX, buttonY + 4, SLIDER_KNOB_WIDTH, BUTTON_HEIGHT - 8, hovered, disabled);

            String label = GuiUtil.shortenText(font, screen.getShadowDistanceButtonLabel(), buttonWidth - 12);
            int textX = buttonX + (buttonWidth - font.getStringWidth(label)) / 2;
            int textY = buttonY + (BUTTON_HEIGHT - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(label, textX, textY, disabled ? 0xA2A2A2 : 0xFFFFFF);

            if (rowHovered) {
                final String tooltip = screen.getShadowDistanceTooltip();
                final int tooltipX = mouseX - 8 - font.getStringWidth(tooltip);
                final int tooltipY = mouseY - 16;
                ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, tooltip, tooltipX, tooltipY));
            }
        }

        @Override
        public void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick) {
            if (!screen.isShadowDistanceControlAvailable()) {
                return;
            }

            if (isWithin(mouseX, mouseY, this.lastSliderX, this.lastSliderY, this.lastSliderWidth, BUTTON_HEIGHT - 4)) {
                GuiUtil.playButtonClickSound();
                this.dragging = true;
                updateFromMouse(mouseX);
            }
        }

        private void mouseReleased() {
            this.dragging = false;
        }

        private void updateFromMouse(int mouseX) {
            float fraction = (mouseX - this.lastSliderX) / (float) Math.max(1, this.lastSliderWidth);
            screen.setShadowDistanceFromSlider(fraction);
        }

        private boolean isWithin(int mouseX, int mouseY, int x, int y, int w, int h) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    public class TopButtonRowEntry implements Entry {
        private boolean refreshHovered;

        private int lastEnableX;
        private int lastEnableY;
        private int lastEnableWidth;
        private int lastRefreshX;
        private int lastRefreshY;

        public boolean allowEnableShadersButton = false;
        public boolean shadersEnabled = false;

        private TopButtonRowEntry() {
        }

        @Override
        public void draw(int slotIndex, int xPos, int yPos, int slotHeight, int mouseX, int mouseY, float partialTicks) {
            int listWidth = contentWidth();
            int enableWidth = listWidth - REFRESH_BUTTON_WIDTH - BUTTON_MARGIN * 3;
            int enableX = xPos + BUTTON_MARGIN;
            int enableY = yPos + BUTTON_MARGIN;
            int refreshX = enableX + enableWidth + BUTTON_MARGIN;
            int refreshY = enableY;

            this.lastEnableX = enableX;
            this.lastEnableY = enableY;
            this.lastEnableWidth = enableWidth;
            this.lastRefreshX = refreshX;
            this.lastRefreshY = refreshY;

            boolean enableHovered = isWithin(mouseX, mouseY, enableX, enableY, enableWidth, BUTTON_HEIGHT);
            this.refreshHovered = isWithin(mouseX, mouseY, refreshX, refreshY, REFRESH_BUTTON_WIDTH, BUTTON_HEIGHT);

            GuiUtil.bindIrisWidgetsTexture();
            GuiUtil.drawButton(enableX, enableY, enableWidth, BUTTON_HEIGHT, enableHovered, !allowEnableShadersButton);

            String label = getEnableDisableLabel();
            FontRenderer font = minecraft.fontRenderer;
            int textX = enableX + (enableWidth - font.getStringWidth(label)) / 2;
            int textY = enableY + (BUTTON_HEIGHT - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(label, textX, textY, 0xFFFFFF);

            GuiUtil.bindIrisWidgetsTexture();
            GuiUtil.drawButton(refreshX, refreshY, REFRESH_BUTTON_WIDTH, BUTTON_HEIGHT, this.refreshHovered, false);
            Icon icon = GuiUtil.Icon.REFRESH;
            int iconX = refreshX + (REFRESH_BUTTON_WIDTH - icon.getWidth()) / 2;
            int iconY = refreshY + (BUTTON_HEIGHT - icon.getHeight()) / 2;
            icon.draw(iconX, iconY);

            if (this.refreshHovered) {
                final String tooltip = I18n.format("options.iris.refreshShaderPacks");
                final int tooltipX = mouseX - 8 - font.getStringWidth(tooltip);
                final int tooltipY = mouseY - 16;
                ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, tooltip, tooltipX, tooltipY));
            }
        }

        @Override
        public void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick) {
            if (isWithin(mouseX, mouseY, lastRefreshX, lastRefreshY, REFRESH_BUTTON_WIDTH, BUTTON_HEIGHT)) {
                GuiUtil.playButtonClickSound();
                ShaderPackSelectionList.this.refresh();
                return;
            }

            if (isWithin(mouseX, mouseY, lastEnableX, lastEnableY, lastEnableWidth, BUTTON_HEIGHT) && allowEnableShadersButton) {
                GuiUtil.playButtonClickSound();
                setShadersEnabled(!shadersEnabled);
            }
        }

        private void setShadersEnabled(boolean value) {
            if (this.shadersEnabled == value) {
                return;
            }

            this.shadersEnabled = value;
            screen.onShadersToggleChanged(value);
        }

        private String getEnableDisableLabel() {
            if (!allowEnableShadersButton) {
                return I18n.format("options.iris.shaders.nonePresent");
            }
            return shadersEnabled ? I18n.format("options.iris.shaders.enabled") : I18n.format("options.iris.shaders.disabled");
        }

        private boolean isWithin(int mouseX, int mouseY, int x, int y, int w, int h) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }

        private int getSlotTop(int slotIndex) {
            return ShaderPackSelectionList.this.top + slotIndex * ShaderPackSelectionList.this.slotHeight + headerPadding;
        }
    }
}

package net.oculus.gui.element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

import net.oculus.gui.GuiUtil;

/**
 * Legacy-friendly recreation of the Iris element row helper. Handles simple
 * horizontal button layouts without relying on the 1.16 pose stack API.
 */
public class IrisElementRow {
    private final Map<Element, Integer> elements = new HashMap<>();
    private final List<Element> orderedElements = new ArrayList<>();
    private final int spacing;
    private int x;
    private int y;
    private int width;
    private int height;

    public IrisElementRow(int spacing) {
        this.spacing = spacing;
    }

    public IrisElementRow() {
        this(1);
    }

    public IrisElementRow add(Element element, int width) {
        if (!this.orderedElements.contains(element)) {
            this.orderedElements.add(element);
        }
        this.elements.put(element, width);

        this.width += width + this.spacing;
        return this;
    }

    public void setWidth(Element element, int width) {
        if (!this.elements.containsKey(element)) {
            return;
        }

        this.width -= this.elements.get(element) + this.spacing;
        add(element, width);
    }

    public void render(int x, int y, int height, int mouseX, int mouseY, float partialTicks, boolean rowHovered) {
        this.x = x;
        this.y = y;
        this.height = height;

        int currentX = x;
        for (Element element : this.orderedElements) {
            int currentWidth = this.elements.get(element);
            boolean hovered = rowHovered && isSectionHovered(currentX, currentWidth, mouseX, mouseY);
            element.render(currentX, y, currentWidth, height, mouseX, mouseY, partialTicks, hovered);
            currentX += currentWidth + this.spacing;
        }
    }

    public void renderRightAligned(int x, int y, int height, int mouseX, int mouseY, float partialTicks, boolean rowHovered) {
        render(x - this.width, y, height, mouseX, mouseY, partialTicks, rowHovered);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return getHovered(mouseX, mouseY).map(element -> element.mouseClicked(mouseX, mouseY, button)).orElse(false);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return getHovered(mouseX, mouseY).map(element -> element.mouseReleased(mouseX, mouseY, button)).orElse(false);
    }

    private boolean isSectionHovered(int sectionX, int sectionWidth, double mouseX, double mouseY) {
        return mouseX >= sectionX && mouseX <= sectionX + sectionWidth && mouseY >= this.y && mouseY <= this.y + this.height;
    }

    private Optional<Element> getHovered(double mouseX, double mouseY) {
        int currentX = this.x;
        for (Element element : this.orderedElements) {
            int currentWidth = this.elements.get(element);
            if (isSectionHovered(currentX, currentWidth, mouseX, mouseY)) {
                return Optional.of(element);
            }
            currentX += currentWidth + this.spacing;
        }
        return Optional.empty();
    }

    public abstract static class Element {
        public boolean disabled;
        private boolean hovered;

        public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            GuiUtil.bindIrisWidgetsTexture();
            GuiUtil.drawButton(x, y, width, height, hovered, this.disabled);
            this.hovered = hovered;
            renderLabel(x, y, width, height, mouseX, mouseY, partialTicks, hovered);
        }

        protected abstract void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered);

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return false;
        }

        public boolean isHovered() {
            return hovered;
        }
    }

    public abstract static class ButtonElement<T extends ButtonElement<T>> extends Element {
        private final Function<T, Boolean> onClick;

        protected ButtonElement(Function<T, Boolean> onClick) {
            this.onClick = onClick;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.disabled || button != 0) {
                return false;
            }

            return onClick.apply(self());
        }

        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }
    }

    public static class IconButtonElement extends ButtonElement<IconButtonElement> {
        public GuiUtil.Icon icon;
        public GuiUtil.Icon hoveredIcon;

        public IconButtonElement(GuiUtil.Icon icon, GuiUtil.Icon hoveredIcon, Function<IconButtonElement, Boolean> onClick) {
            super(onClick);
            this.icon = icon;
            this.hoveredIcon = hoveredIcon;
        }

        public IconButtonElement(GuiUtil.Icon icon, Function<IconButtonElement, Boolean> onClick) {
            this(icon, icon, onClick);
        }

        @Override
        protected void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            int iconX = x + (width - this.icon.getWidth()) / 2;
            int iconY = y + (height - this.icon.getHeight()) / 2;

            GuiUtil.bindIrisWidgetsTexture();
            if (!this.disabled && hovered) {
                this.hoveredIcon.draw(iconX, iconY);
            } else {
                this.icon.draw(iconX, iconY);
            }
        }
    }

    public static class TextButtonElement extends ButtonElement<TextButtonElement> {
        private final FontRenderer font;
        public ITextComponent text;

        public TextButtonElement(ITextComponent text, Function<TextButtonElement, Boolean> onClick) {
            super(onClick);
            this.font = Minecraft.getMinecraft().fontRenderer;
            this.text = text;
        }

        @Override
        protected void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            String rendered = text.getFormattedText();
            int textX = x + (width - this.font.getStringWidth(rendered)) / 2;
            int textY = y + (height - this.font.FONT_HEIGHT) / 2;
            this.font.drawStringWithShadow(rendered, textX, textY, 0xFFFFFF);
        }
    }
}

package net.oculus.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderPackLanguageLookup;

/**
 * Centralised helpers for GUI rendering and interactions, translated for 1.12.2.
 */
public final class GuiUtil {
    public static final ResourceLocation IRIS_WIDGETS_TEX = new ResourceLocation("iris", "textures/gui/widgets.png");
    private static final String ELLIPSIS = "...";

    private GuiUtil() {
    }

    private static Minecraft client() {
        return Minecraft.getMinecraft();
    }

    public static void bindIrisWidgetsTexture() {
        client().getTextureManager().bindTexture(IRIS_WIDGETS_TEX);
    }

    public static void drawButton(int x, int y, int width, int height, boolean hovered, boolean disabled) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        int vOffset = disabled ? 46 : hovered ? 86 : 66;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();

        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, vOffset, halfWidth, halfHeight, 256, 256);
        Gui.drawModalRectWithCustomSizedTexture(x + halfWidth, y, 200 - (width - halfWidth), vOffset, width - halfWidth, halfHeight, 256, 256);
        Gui.drawModalRectWithCustomSizedTexture(x, y + halfHeight, 0, vOffset + (20 - (height - halfHeight)), halfWidth, height - halfHeight, 256, 256);
        Gui.drawModalRectWithCustomSizedTexture(x + halfWidth, y + halfHeight, 200 - (width - halfWidth), vOffset + (20 - (height - halfHeight)), width - halfWidth, height - halfHeight, 256, 256);
    }

    public static void drawPanel(int x, int y, int width, int height) {
        int borderColor = 0xDEDEDEDE;
        int innerColor = 0xDE000000;

        Gui.drawRect(x, y, x + width, y + 1, borderColor);
        Gui.drawRect(x, y + height - 1, x + width, y + height, borderColor);
        Gui.drawRect(x, y + 1, x + 1, y + height - 1, borderColor);
        Gui.drawRect(x + width - 1, y + 1, x + width, y + height - 1, borderColor);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, innerColor);
    }

    public static void drawTextPanel(FontRenderer font, ITextComponent text, int x, int y) {
        String rendered = text.getFormattedText();
        drawPanel(x, y, font.getStringWidth(rendered) + 8, 16);
        font.drawStringWithShadow(rendered, x + 4, y + 4, 0xFFFFFF);
    }

    public static void drawTextPanel(FontRenderer font, String text, int x, int y) {
        drawPanel(x, y, font.getStringWidth(text) + 8, 16);
        font.drawStringWithShadow(text, x + 4, y + 4, 0xFFFFFF);
    }

    public static String shortenText(FontRenderer font, String text, int width) {
        if (font.getStringWidth(text) <= width) {
            return text;
        }

        int ellipsisWidth = font.getStringWidth(ELLIPSIS);
        String trimmed = font.trimStringToWidth(text, Math.max(0, width - ellipsisWidth));
        return trimmed + ELLIPSIS;
    }

    public static ITextComponent shortenText(FontRenderer font, ITextComponent text, int width) {
        String rendered = text.getFormattedText();
        if (font.getStringWidth(rendered) <= width) {
            return text.createCopy();
        }

        int ellipsisWidth = font.getStringWidth(ELLIPSIS);
        String trimmed = font.trimStringToWidth(rendered, Math.max(0, width - ellipsisWidth));
        TextComponentString shortened = new TextComponentString(trimmed + ELLIPSIS);
        Style style = text.getStyle();
        if (style != null) {
            shortened.setStyle(style.createDeepCopy());
        }

        return shortened;
    }

    public static ITextComponent translateOrDefault(ITextComponent defaultText, String translationKey, Object... format) {
        if (I18n.hasKey(translationKey)) {
            return new TextComponentTranslation(translationKey, format);
        }

        return defaultText.createCopy();
    }

    public static ITextComponent translateShaderPackOrDefault(ShaderPack pack, ITextComponent defaultText, String translationKey, Object... format) {
        if (I18n.hasKey(translationKey)) {
            return new TextComponentTranslation(translationKey, format);
        }

        String shaderPackTranslation = ShaderPackLanguageLookup.lookup(pack, translationKey);
        if (shaderPackTranslation != null) {
            return new TextComponentString(ShaderPackLanguageLookup.formatLenient(shaderPackTranslation, format));
        }

        return defaultText.createCopy();
    }

    public static ITextComponent translateShaderPack(ShaderPack pack, String translationKey, Object... format) {
        String shaderPackTranslation = ShaderPackLanguageLookup.lookup(pack, translationKey);
        if (shaderPackTranslation == null) {
            return null;
        }

        return new TextComponentString(ShaderPackLanguageLookup.formatLenient(shaderPackTranslation, format));
    }

    public static void playButtonClickSound() {
        client().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static class Icon {
        public static final Icon SEARCH = new Icon(0, 0, 7, 8);
        public static final Icon CLOSE = new Icon(7, 0, 5, 6);
        public static final Icon REFRESH = new Icon(12, 0, 10, 10);
        public static final Icon EXPORT = new Icon(22, 0, 7, 8);
        public static final Icon EXPORT_COLORED = new Icon(29, 0, 7, 8);
        public static final Icon IMPORT = new Icon(22, 8, 7, 8);
        public static final Icon IMPORT_COLORED = new Icon(29, 8, 7, 8);

        private final int u;
        private final int v;
        private final int width;
        private final int height;

        public Icon(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public void draw(int x, int y) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();

            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, 256, 256);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}

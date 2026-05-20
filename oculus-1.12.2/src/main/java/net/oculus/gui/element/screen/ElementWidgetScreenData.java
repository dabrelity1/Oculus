package net.oculus.gui.element.screen;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * Simple data transfer object describing the heading and navigation controls
 * for a shader option screen.
 */
public class ElementWidgetScreenData {
    public static final ElementWidgetScreenData EMPTY = new ElementWidgetScreenData(new TextComponentString(""), true);

    public final ITextComponent heading;
    public final boolean backButton;

    public ElementWidgetScreenData(ITextComponent heading, boolean backButton) {
        this.heading = heading;
        this.backButton = backButton;
    }
}

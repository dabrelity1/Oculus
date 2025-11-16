package net.oculus.gui.element.widget;

import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.shaderpack.option.menu.OptionMenuElement;

/**
 * 1.12.2 friendly version of the widget abstraction used by the shader pack
 * option menu. Rendering occurs without pose stacks to match the legacy GUI
 * toolkit.
 */
public abstract class AbstractElementWidget<T extends OptionMenuElement> {
    protected final T element;

    public static final AbstractElementWidget<OptionMenuElement> EMPTY = new AbstractElementWidget<OptionMenuElement>(OptionMenuElement.EMPTY) {
        @Override
        public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
        }
    };

    protected AbstractElementWidget(T element) {
        this.element = element;
    }

    public void init(ShaderPackScreen screen, NavigationController navigation) {
        // Hook for widgets that need screen references.
    }

    public abstract void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered);

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return false;
    }
}

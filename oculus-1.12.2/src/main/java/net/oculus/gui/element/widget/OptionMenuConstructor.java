package net.oculus.gui.element.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.gui.element.ShaderPackOptionList;
import net.oculus.gui.element.screen.ElementWidgetScreenData;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;
import net.oculus.shaderpack.option.menu.OptionMenuElement;
import net.oculus.shaderpack.option.menu.OptionMenuElementScreen;
import net.oculus.shaderpack.option.menu.OptionMenuLinkElement;
import net.oculus.shaderpack.option.menu.OptionMenuMainElementScreen;
import net.oculus.shaderpack.option.menu.OptionMenuOptionElement;
import net.oculus.shaderpack.option.menu.OptionMenuProfileElement;
import net.oculus.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.oculus.shaderpack.option.menu.OptionMenuSubElementScreen;

public final class OptionMenuConstructor {
    private OptionMenuConstructor() {
    }

    public static void constructAndApplyToScreen(OptionMenuContainer container, ShaderPackScreen packScreen, ShaderPackOptionList optionList, NavigationController navigation) {
        if (container == null) {
            ITextComponent heading = GuiUtil.translateOrDefault(new TextComponentString("No shader options available"), "options.iris.noShaderOptions");
            optionList.addHeader(heading, navigation.hasHistory());
            optionList.addWidgets(1, Collections.singletonList(AbstractElementWidget.EMPTY));
            return;
        }

        OptionMenuElementScreen screen = container.mainScreen != null ? container.mainScreen : OptionMenuElementScreen.EMPTY;
        if (navigation.getCurrentScreen() != null && container.subScreens.containsKey(navigation.getCurrentScreen())) {
            screen = container.subScreens.get(navigation.getCurrentScreen());
        }

        ElementWidgetScreenData data = createScreenData(screen, packScreen);
        List<OptionMenuElement> elements = new ArrayList<>(screen.elements);
        attachPendingValueSuppliers(elements, packScreen);

        optionList.addHeader(data.heading, data.backButton);

        int columns = Math.max(1, screen.getColumnCount());
        List<AbstractElementWidget<?>> widgets = elements.isEmpty()
            ? Collections.singletonList(AbstractElementWidget.EMPTY)
            : createWidgets(elements, packScreen, navigation);

        optionList.addWidgets(columns, widgets);
    }

    private static ElementWidgetScreenData createScreenData(OptionMenuElementScreen screen, ShaderPackScreen packScreen) {
        if (screen instanceof OptionMenuSubElementScreen) {
            String screenId = ((OptionMenuSubElementScreen) screen).screenId;
            return new ElementWidgetScreenData(
                GuiUtil.translateShaderPackOrDefault(packScreen != null ? packScreen.getCurrentPack() : null,
                    new TextComponentString(screenId), "screen." + screenId),
                true
            );
        }

        ShaderPack pack = packScreen != null ? packScreen.getCurrentPack() : null;
        String packName = pack != null ? pack.getName() : "Shader Options";
        ITextComponent heading = new TextComponentString(packName);
        Style style = heading.getStyle();
        if (style == null) {
            style = new Style();
            heading.setStyle(style);
        }
        if (screen instanceof OptionMenuMainElementScreen) {
            style.setBold(true).setColor(TextFormatting.WHITE);
        }
        return new ElementWidgetScreenData(heading, false);
    }

    private static void attachPendingValueSuppliers(List<OptionMenuElement> elements, ShaderPackScreen packScreen) {
        if (packScreen == null) {
            return;
        }

        for (OptionMenuElement element : elements) {
            if (element instanceof OptionMenuProfileElement) {
                ((OptionMenuProfileElement) element).setPendingValuesSupplier(packScreen::getWorkingOptionValues);
            } else if (element instanceof OptionMenuOptionElement) {
                ((OptionMenuOptionElement) element).setPendingValuesSupplier(packScreen::getWorkingOptionValues);
            }
        }
    }

    private static List<AbstractElementWidget<?>> createWidgets(List<OptionMenuElement> elements, ShaderPackScreen screen, NavigationController navigation) {
        List<AbstractElementWidget<?>> widgets = new ArrayList<>();
        for (OptionMenuElement element : elements) {
            AbstractElementWidget<?> widget;
            if (element instanceof OptionMenuProfileElement) {
                widget = new ProfileElementWidget((OptionMenuProfileElement) element);
            } else if (element instanceof OptionMenuBooleanOptionElement) {
                widget = new BooleanElementWidget((OptionMenuBooleanOptionElement) element);
            } else if (element instanceof OptionMenuStringOptionElement) {
                OptionMenuStringOptionElement stringElement = (OptionMenuStringOptionElement) element;
                widget = stringElement.slider ? new SliderElementWidget(stringElement) : new StringElementWidget(stringElement);
            } else if (element instanceof OptionMenuLinkElement) {
                widget = new LinkElementWidget((OptionMenuLinkElement) element);
            } else {
                widget = new PlaceholderWidget(element);
            }
            widget.init(screen, navigation);
            widgets.add(widget);
        }
        return widgets;
    }

    private static class PlaceholderWidget extends AbstractElementWidget<OptionMenuElement> {
        private PlaceholderWidget(OptionMenuElement element) {
            super(element);
        }

        @Override
        public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            // Intentionally blank for OptiFine's <empty> menu spacer element.
        }
    }
}

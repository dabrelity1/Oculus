package net.oculus.gui.element.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.gui.element.ShaderPackOptionList;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;
import net.oculus.shaderpack.option.menu.OptionMenuElement;
import net.oculus.shaderpack.option.menu.OptionMenuElementScreen;
import net.oculus.shaderpack.option.menu.OptionMenuProfileElement;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.option.values.OptionValues;

/**
 * Temporary shim that builds a minimal option list layout until the full option
 * menu parsing layer is ported. It keeps the GUI wiring identical to the modern
 * implementation so the remaining pieces can slot in later without changing
 * this class.
 */
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

        List<OptionMenuElement> elements = new ArrayList<>(screen.elements);
        attachProfileWidget(elements, packScreen);

        ITextComponent heading = new TextComponentString(screen.getScreenId() != null ? screen.getScreenId() : "Shader Options");
        optionList.addHeader(heading, navigation.hasHistory());

        int columns = Math.max(1, screen.getColumnCount());
        List<AbstractElementWidget<?>> widgets = elements.isEmpty()
            ? Collections.singletonList(AbstractElementWidget.EMPTY)
            : createWidgets(elements);

        optionList.addWidgets(columns, widgets);
    }

    private static void attachProfileWidget(List<OptionMenuElement> elements, ShaderPackScreen packScreen) {
        if (packScreen == null) {
            return;
        }

        ShaderPack pack = packScreen.getCurrentPack();
        ProfileSet profiles = pack != null ? pack.getProfileSet() : ProfileSet.empty();
        if (profiles == null || profiles.size() == 0) {
            return;
        }

        MutableOptionValues workingValues = packScreen.getWorkingOptionValues();
        OptionValues appliedValues = pack != null ? pack.getOptionValues() : null;
        OptionSet optionSet = resolveOptionSet(appliedValues, workingValues);
        if (optionSet == null) {
            return;
        }

        boolean hasProfileElement = false;
        for (OptionMenuElement element : elements) {
            if (element instanceof OptionMenuProfileElement) {
                ((OptionMenuProfileElement) element).setPendingValuesSupplier(packScreen::getWorkingOptionValues);
                hasProfileElement = true;
            }
        }

        if (!hasProfileElement) {
            OptionValues applied = appliedValues != null ? appliedValues : (workingValues != null ? workingValues : new MutableOptionValues(optionSet));
            OptionMenuProfileElement profileElement = new OptionMenuProfileElement(profiles, optionSet, applied);
            profileElement.setPendingValuesSupplier(packScreen::getWorkingOptionValues);
            elements.add(profileElement);
        }
    }

    private static OptionSet resolveOptionSet(OptionValues appliedValues, MutableOptionValues workingValues) {
        if (appliedValues != null && appliedValues.getOptionSet() != null) {
            return appliedValues.getOptionSet();
        }
        if (workingValues != null) {
            return workingValues.getOptionSet();
        }
        return null;
    }

    private static List<AbstractElementWidget<?>> createWidgets(List<OptionMenuElement> elements) {
        List<AbstractElementWidget<?>> widgets = new ArrayList<>();
        for (OptionMenuElement element : elements) {
            if (element instanceof OptionMenuProfileElement) {
                widgets.add(new ProfileElementWidget((OptionMenuProfileElement) element));
                continue;
            }
            widgets.add(new PlaceholderWidget(element));
        }
        return widgets;
    }

    private static class PlaceholderWidget extends AbstractElementWidget<OptionMenuElement> {
        private PlaceholderWidget(OptionMenuElement element) {
            super(element);
        }

        @Override
        public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
            // Placeholder widget draws nothing; actual widgets will be ported soon.
        }
    }
}

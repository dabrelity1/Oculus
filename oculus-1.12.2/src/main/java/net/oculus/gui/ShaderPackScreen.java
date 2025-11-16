package net.oculus.gui;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.Objects;
import java.util.Properties;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.oculus.Oculus;
import net.oculus.config.OculusConfig;
import net.oculus.gui.element.ShaderPackOptionList;
import net.oculus.pipeline.PipelineManager;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.oculus.shaderpack.IdMap;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderPackLoader;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.option.Profile;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.texture.TextureStage;

/**
 * 1.12-compatible skeleton for the shader pack selection screen. The UI wiring
 * mirrors the modern Iris implementation but defers functional pieces until the
 * remaining frontend widgets are ported.
 */
public class ShaderPackScreen extends GuiScreen {
    public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();

    private static final int COMMENT_PANEL_WIDTH = 314;
    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM_MARGIN = 58;
    private static final int LAYOUT_MARGIN = 12;
    private static final int PACK_LIST_MAX_WIDTH = 308;
    private static final int OPTION_LIST_MAX_WIDTH = 400;
    private static final String SETTINGS_FILE_FILTER_LABEL = "Shader Pack Settings (.txt)";

    private static final ITextComponent SELECT_TITLE = createSubtitle("pack.iris.select.title");
    private static final ITextComponent CONFIGURE_TITLE = createSubtitle("pack.iris.configure.title");

    private final GuiScreen parent;
    private final OculusConfig config;
    private final ITextComponent oculusTextComponent;
    private final ITextComponent titleComponent;

    private GuiButton screenSwitchButton;
    private GuiButton guiToggleButton;
    private NavigationController navigation;
    private ShaderPackSelectionList packList;
    private ShaderPackOptionList optionList;
    private ShaderPack currentPack;
    private String selectedPackName;

    private ITextComponent notificationDialog;
    private int notificationDialogTimer;

    private boolean optionMenuOpen;
    private boolean dropChanges;
    private boolean guiHidden;

    private float guiButtonHoverTimer;

    private Object hoveredElement;
    private Optional<ITextComponent> hoveredElementCommentTitle = Optional.empty();
    private List<String> hoveredElementCommentBody = new ArrayList<>();
    private int hoveredElementCommentTimer;

    private ITextComponent developmentComponent;
    private ITextComponent updateComponent;

    private boolean appliedThisSession;
    private boolean pendingShadersEnabled;
    private MutableOptionValues workingOptionValues = ShaderPack.createEmptyOptionValues();
    private ShaderPack baselinePack;
    private String baselinePackName;
    private boolean baselineShadersEnabled;
    private MutableOptionValues baselineOptionValues = ShaderPack.createEmptyOptionValues();
    private Map<String, Map<String, String>> baselineOptionOverrides = new HashMap<>();

    public ShaderPackScreen(GuiScreen parent) {
        this.parent = parent;
        this.config = Oculus.getConfig();
        this.titleComponent = new TextComponentTranslation("options.iris.shaderPackSelection.title");

        String oculusVersion = "Oculus"; // Placeholder until version wiring is implemented
        TextComponentString base = new TextComponentString(oculusVersion);
        Style baseStyle = new Style().setColor(TextFormatting.GRAY);
        base.setStyle(baseStyle);
        this.oculusTextComponent = base;

        ShaderPack activePack = PipelineManager.INSTANCE.getActivePack();
        if (activePack == null) {
            activePack = ShaderPackLoader.internalPack();
        }

        this.pendingShadersEnabled = this.config != null
            ? this.config.areShadersEnabled()
            : (activePack != null && !activePack.isInternal());

        this.selectedPackName = determineInitialSelection(activePack);
        this.currentPack = preloadSelectedPack(activePack);
        refreshWorkingOptionValuesFromCurrentPack();
        this.navigation = new NavigationController(this.currentPack.getMenuContainer());
        this.appliedThisSession = true;

        refreshForChangedPack();
        captureBaselineState("init");
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int bottomCenter = this.width / 2 - 50;
        int topCenter = this.width / 2 - 76;

        this.buttonList.add(new GuiButton(0, bottomCenter + 104, this.height - 27, 100, 20, I18n.format("gui.done")));
        this.buttonList.add(new GuiButton(1, bottomCenter, this.height - 27, 100, 20, I18n.format("options.iris.apply")));
        this.buttonList.add(new GuiButton(2, bottomCenter - 104, this.height - 27, 100, 20, I18n.format("gui.cancel")));
        this.buttonList.add(new GuiButton(3, topCenter - 78, this.height - 51, 152, 20, I18n.format("options.iris.openShaderPackFolder")));

    this.screenSwitchButton = new GuiButton(4, topCenter + 78, this.height - 51, 152, 20, I18n.format("options.iris.shaderPackList"));
        this.buttonList.add(this.screenSwitchButton);

        if (this.mc.world != null) {
            String label = this.guiHidden ? I18n.format("options.iris.gui.show") : I18n.format("options.iris.gui.hide");
            this.guiToggleButton = new GuiButton(5, this.width - 110, this.height - 39, 100, 20, label);
            this.buttonList.add(this.guiToggleButton);
        } else {
            this.guiToggleButton = null;
        }

        int listTop = LIST_TOP;
        int listBottom = Math.max(listTop + 1, this.height - LIST_BOTTOM_MARGIN);

        if (this.packList == null) {
            this.packList = new ShaderPackSelectionList(this, this.mc, this.width, this.height, listTop, listBottom);
        } else {
            this.packList.resize(this.width, this.height, listTop, listBottom);
        }

        if (this.navigation == null) {
            this.navigation = new NavigationController(this.currentPack.getMenuContainer());
        }

        if (this.optionList == null) {
            this.optionList = new ShaderPackOptionList(this, this.navigation, this.currentPack, this.workingOptionValues, this.mc, this.width, this.height, listTop, listBottom);
        } else {
            this.optionList.resize(this.width, this.height, listTop, listBottom);
            this.optionList.updateNavigation(this.navigation);
            this.optionList.applyShaderPack(this.currentPack, this.workingOptionValues);
        }

        if (this.optionList != null) {
            ShaderProperties properties = this.currentPack != null ? this.currentPack.getProperties() : ShaderProperties.empty();
            this.optionList.setPackOptions(properties);
        }

        if (this.packList != null && this.currentPack != null) {
            this.packList.markAppliedPack(this.currentPack.getName());
        }

        syncShaderToggleWithConfig();

        layoutLists();

        if (this.optionList != null) {
            if (this.navigation != null) {
                this.navigation.setActiveOptionList(this.optionList);
                this.navigation.refresh();
            } else {
                this.optionList.refresh();
            }
        }

        if (this.selectedPackName != null) {
            this.packList.select(this.selectedPackName);
        }

        refreshScreenSwitchButton();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.mc.world == null) {
            this.drawDefaultBackground();
        } else if (!this.guiHidden) {
            this.drawGradientRect(0, 0, this.width, this.height, 0x4F232323, 0x4F232323);
        }

        if (!this.guiHidden) {
            if (!this.optionMenuOpen && this.packList != null) {
                this.packList.drawScreen(mouseX, mouseY, partialTicks);
            } else if (this.optionMenuOpen && this.optionList != null) {
                this.optionList.drawScreen(mouseX, mouseY, partialTicks);
            }
        }

        float previousHoverTimer = this.guiButtonHoverTimer;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (previousHoverTimer == this.guiButtonHoverTimer) {
            this.guiButtonHoverTimer = 0.0F;
        }

        if (!this.guiHidden) {
            this.drawCenteredString(this.fontRenderer, this.titleComponent.getFormattedText(), this.width / 2, 8, 0xFFFFFF);

            if (this.notificationDialog != null && this.notificationDialogTimer > 0) {
                this.drawCenteredString(this.fontRenderer, this.notificationDialog.getFormattedText(), this.width / 2, 21, 0xFFFFFF);
            } else {
                ITextComponent subtitle = this.optionMenuOpen ? CONFIGURE_TITLE : SELECT_TITLE;
                this.drawCenteredString(this.fontRenderer, subtitle.getFormattedText(), this.width / 2, 21, 0xFFFFFF);
            }

            if (isDisplayingComment()) {
                drawCommentPanel();
            }
        }

        for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
            render.run();
        }
        TOP_LAYER_RENDER_QUEUE.clear();

        if (this.developmentComponent != null) {
            this.fontRenderer.drawStringWithShadow(this.developmentComponent.getFormattedText(), 2, this.height - 10, 0xFFFFFF);
            this.fontRenderer.drawStringWithShadow(this.oculusTextComponent.getFormattedText(), 2, this.height - 20, 0xFFFFFF);
        } else if (this.updateComponent != null) {
            this.fontRenderer.drawStringWithShadow(this.updateComponent.getFormattedText(), 2, this.height - 10, 0xFFFFFF);
            this.fontRenderer.drawStringWithShadow(this.oculusTextComponent.getFormattedText(), 2, this.height - 20, 0xFFFFFF);
        } else {
            this.fontRenderer.drawStringWithShadow(this.oculusTextComponent.getFormattedText(), 2, this.height - 10, 0xFFFFFF);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }

        switch (button.id) {
            case 0: // Done
                this.dropChanges = false;
                applyChanges();
                this.mc.displayGuiScreen(this.parent);
                break;
            case 1: // Apply
                this.applyChanges();
                break;
            case 2: // Cancel
                dropChangesAndClose();
                break;
            case 3: // Open shader pack folder
                openShaderPackFolder();
                break;
            case 4: // Switch screen
                this.optionMenuOpen = !this.optionMenuOpen;
                this.initGui();
                break;
            case 5: // Toggle GUI visibility
                this.guiHidden = !this.guiHidden;
                if (this.guiToggleButton != null) {
                    this.guiToggleButton.displayString = this.guiHidden
                        ? I18n.format("options.iris.gui.show")
                        : I18n.format("options.iris.gui.hide");
                }
                this.initGui();
                break;
            default:
                break;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (this.notificationDialogTimer > 0) {
            this.notificationDialogTimer--;
        }

        if (this.hoveredElement != null) {
            this.hoveredElementCommentTimer++;
        } else {
            this.hoveredElementCommentTimer = 0;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.guiHidden) {
                this.guiHidden = false;
                this.initGui();
                return;
            } else if (this.optionMenuOpen) {
                this.optionMenuOpen = false;
                this.initGui();
                return;
            } else {
                dropChangesAndClose();
                return;
            }
        }

        if (!this.guiHidden) {
            if (!this.optionMenuOpen && this.packList != null) {
                this.packList.keyTyped(typedChar, keyCode);
            } else if (this.optionMenuOpen && this.optionList != null) {
                this.optionList.keyTyped(typedChar, keyCode);
            }
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void onGuiClosed() {
        if (!this.dropChanges) {
            if (!this.appliedThisSession) {
                applyChanges();
            }
        } else {
            discardChanges();
        }

        this.appliedThisSession = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (!this.guiHidden) {
            if (!this.optionMenuOpen && this.packList != null) {
                this.packList.handleMouseInput();
            } else if (this.optionMenuOpen && this.optionList != null) {
                this.optionList.handleMouseInput();
            }
        }

        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!this.guiHidden) {
            if (!this.optionMenuOpen && this.packList != null) {
                this.packList.mouseClicked(mouseX, mouseY, mouseButton);
            } else if (this.optionMenuOpen && this.optionList != null) {
                this.optionList.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (!this.guiHidden) {
            if (!this.optionMenuOpen && this.packList != null) {
                this.packList.mouseReleased(mouseX, mouseY, state);
            } else if (this.optionMenuOpen && this.optionList != null) {
                this.optionList.mouseReleased(mouseX, mouseY, state);
            }
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    public void onFilesDrop(List<Path> paths) {
        // File drag-and-drop integration is deferred until the selection list is ported.
    }

    public void displayNotification(ITextComponent component) {
        this.notificationDialog = component;
        this.notificationDialogTimer = 100;
    }

    public void setElementHoveredStatus(Object widget, boolean hovered) {
        if (hovered && widget != this.hoveredElement) {
            this.hoveredElement = widget;
            this.hoveredElementCommentTitle = Optional.empty();
            this.hoveredElementCommentBody.clear();
            this.hoveredElementCommentTimer = 0;
        } else if (!hovered && widget == this.hoveredElement) {
            this.hoveredElement = null;
            this.hoveredElementCommentTitle = Optional.empty();
            this.hoveredElementCommentBody.clear();
            this.hoveredElementCommentTimer = 0;
        }
    }

    public boolean isDisplayingComment() {
        if (this.hoveredElement == null) {
            return false;
        }
        if (this.hoveredElementCommentTimer <= 20) {
            return false;
        }
        return this.hoveredElementCommentTitle.isPresent() || !this.hoveredElementCommentBody.isEmpty();
    }

    public ShaderPack getCurrentPack() {
        return this.currentPack != null ? this.currentPack : ShaderPack.placeholder();
    }

    public MutableOptionValues getWorkingOptionValues() {
        return this.workingOptionValues;
    }

    void onShaderPackSelected(String packName) {
        this.selectedPackName = packName;
        loadShaderPack(packName);
        persistSelectedPack(true);
        refreshForChangedPack();
    }

    public boolean resetCurrentPackOptions() {
        if (this.currentPack == null) {
            ITextComponent missingPack = GuiUtil.translateOrDefault(
                new TextComponentString("Select a shader pack before resetting options."),
                "options.iris.reset.noPack"
            );
            Style missingPackStyle = missingPack.getStyle();
            if (missingPackStyle == null) {
                missingPackStyle = new Style();
                missingPack.setStyle(missingPackStyle);
            }
            missingPackStyle.setColor(TextFormatting.RED);
            displayNotification(missingPack);
            return false;
        }

        MutableOptionValues optionValues = this.workingOptionValues;
        if (optionValues == null) {
            ITextComponent unavailable = GuiUtil.translateOrDefault(
                new TextComponentString("Shader pack does not expose configurable options."),
                "options.iris.reset.unavailable"
            );
            Style unavailableStyle = unavailable.getStyle();
            if (unavailableStyle == null) {
                unavailableStyle = new Style();
                unavailable.setStyle(unavailableStyle);
            }
            unavailableStyle.setColor(TextFormatting.RED);
            displayNotification(unavailable);
            return false;
        }

        if (optionValues.getOptionsChanged() == 0) {
            ITextComponent noop = GuiUtil.translateOrDefault(
                new TextComponentString("All shader options already match their defaults."),
                "options.iris.reset.noChanges"
            );
            Style style = noop.getStyle();
            if (style == null) {
                style = new Style();
                noop.setStyle(style);
            }
            style.setColor(TextFormatting.GRAY);
            displayNotification(noop);
            return false;
        }

        optionValues.clearAll();

        if (this.config != null) {
            String packName = this.currentPack != null ? this.currentPack.getName() : null;
            this.config.clearOptionOverrides(packName);
        } else {
            Oculus.LOGGER.debug("Resetting shader options without config available; overrides cleared only in-memory");
        }

        markPendingChanges();

        if (this.optionList != null) {
            this.optionList.refresh();
        }
        if (this.navigation != null) {
            this.navigation.refresh();
        }

        ITextComponent message = GuiUtil.translateOrDefault(
            new TextComponentString("Shader options reset. Press Apply to finish."),
            "options.iris.reset.pendingApply"
        );
        Style messageStyle = message.getStyle();
        if (messageStyle == null) {
            messageStyle = new Style();
            message.setStyle(messageStyle);
        }
        messageStyle.setColor(TextFormatting.YELLOW);
        displayNotification(message);
        return true;
    }

    public void beginImportSettingsFlow() {
        if (!validateImportPreconditions()) {
            return;
        }

        ShaderPack pack = this.currentPack;
        String packName = pack != null ? pack.getName() : this.selectedPackName;
        Path defaultPath = buildDefaultSettingsPath(packName);
        String dialogTitle = I18n.format("options.iris.importSettings.tooltip");

        Oculus.LOGGER.info("Opening shader settings import dialog for pack {}", packName);

        FileDialogUtil.fileSelectDialog(
            FileDialogUtil.DialogType.OPEN,
            dialogTitle,
            defaultPath,
            SETTINGS_FILE_FILTER_LABEL,
            "*.txt"
        ).thenCompose(optionalPath -> optionalPath
            .map(path -> CompletableFuture.supplyAsync(() -> loadImportFile(path)))
            .orElseGet(() -> CompletableFuture.completedFuture(ImportFileResult.skipped())))
        .whenComplete((result, throwable) -> scheduleOnClient(() -> handleImportResult(result, throwable)));
    }

    public void beginExportSettingsFlow() {
        if (!validateExportPreconditions()) {
            return;
        }

        ShaderPack pack = this.currentPack;
        String packName = pack != null ? pack.getName() : this.selectedPackName;
        Path defaultPath = buildDefaultSettingsPath(packName);
        String dialogTitle = I18n.format("options.iris.exportSettings.tooltip");
        final Map<String, String> snapshot = new HashMap<>(this.workingOptionValues.asMap());

        Oculus.LOGGER.info("Opening shader settings export dialog for pack {}", packName);

        FileDialogUtil.fileSelectDialog(
            FileDialogUtil.DialogType.SAVE,
            dialogTitle,
            defaultPath,
            SETTINGS_FILE_FILTER_LABEL,
            "*.txt"
        ).thenCompose(optionalPath -> optionalPath
            .map(path -> CompletableFuture.supplyAsync(() -> writeExportFile(path, snapshot)))
            .orElseGet(() -> CompletableFuture.completedFuture(ExportFileResult.skipped())))
        .whenComplete((result, throwable) -> scheduleOnClient(() -> handleExportResult(result, throwable)));
    }

    private void dropChangesAndClose() {
        this.dropChanges = true;
        this.mc.displayGuiScreen(this.parent);
    }

    private void applyChanges() {
        ShaderPack selectedPack = this.currentPack;
        boolean hasExternalPack = selectedPack != null && !selectedPack.isInternal();
        boolean enableShaders = this.pendingShadersEnabled && hasExternalPack;

        ShaderPack packToApply = enableShaders && selectedPack != null
            ? selectedPack
            : ShaderPackLoader.internalPack();

    PipelineManager.INSTANCE.reloadShaderPack(packToApply, this.workingOptionValues);

        this.pendingShadersEnabled = enableShaders;
        this.appliedThisSession = true;
        this.dropChanges = false;
        if (selectedPack != null) {
            this.selectedPackName = selectedPack.getName();
        }

        if (this.packList != null) {
            this.packList.markAppliedPack(this.selectedPackName);
        }

        updateConfigAfterApply(enableShaders);
        refreshWorkingOptionValuesFromCurrentPack(true);
        refreshForChangedPack();
        captureBaselineState("apply");
    }

    private void discardChanges() {
        Oculus.LOGGER.debug("Discarding pending shader GUI changes; restoring baseline pack {}", this.baselinePackName);

        this.dropChanges = false;
        this.appliedThisSession = true;

        this.currentPack = resolveBaselinePack();
        this.selectedPackName = this.baselinePackName;
        this.pendingShadersEnabled = this.baselineShadersEnabled;

        if (this.config != null) {
            this.config.clearShaderOptionOverrides();
            this.baselineOptionOverrides.forEach((pack, overrides) -> this.config.setOptionOverrides(pack, overrides));
            this.config.setSelectedPackName(this.baselinePackName);
            this.config.setShadersEnabled(this.baselineShadersEnabled);
            saveConfig("discard");
        }

        this.navigation = new NavigationController(this.currentPack.getMenuContainer());
        this.workingOptionValues = this.baselineOptionValues != null
            ? this.baselineOptionValues.mutableCopy()
            : ShaderPack.createEmptyOptionValues();
        refreshForChangedPack();

        if (this.packList != null && this.selectedPackName != null) {
            this.packList.markAppliedPack(this.selectedPackName);
        }
    }

    private void openShaderPackFolder() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameDir == null) {
            Oculus.LOGGER.warn("Cannot open shader pack folder because Minecraft isn't fully initialized");
            return;
        }

        final Path shaderPackDir = minecraft.gameDir.toPath().resolve("shaderpacks");

        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(shaderPackDir);
                openFolderWithOperatingSystem(shaderPackDir);
            } catch (Exception exception) {
                Oculus.LOGGER.error("Failed to open shader pack folder {}", shaderPackDir, exception);
                minecraft.addScheduledTask(() -> displayNotification(createFolderFailureNotification()));
            }
        });
    }

    private void openFolderWithOperatingSystem(Path directory) throws IOException {
        Util.EnumOS os = Util.getOSType();
        String absolutePath = directory.toFile().getAbsolutePath();

        switch (os) {
            case OSX:
                new ProcessBuilder("/usr/bin/open", absolutePath).start();
                break;
            case WINDOWS:
                new ProcessBuilder("cmd", "/c", "start", "", absolutePath).start();
                break;
            case LINUX:
                new ProcessBuilder("xdg-open", absolutePath).start();
                break;
            default:
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(directory.toFile());
                } else {
                    throw new IOException("Unsupported operating system: " + os);
                }
                break;
        }
    }

    private ITextComponent createFolderFailureNotification() {
        ITextComponent component = GuiUtil.translateOrDefault(
            new TextComponentString("Failed to open shaderpacks folder. Check logs."),
            "options.iris.openShaderPackFolderFailed"
        );
        Style style = component.getStyle();
        if (style == null) {
            style = new Style();
            component.setStyle(style);
        }
        style.setColor(TextFormatting.RED);
        return component;
    }

    private void refreshForChangedPack() {
        if (this.packList != null) {
            this.packList.refresh();
            if (this.selectedPackName != null) {
                this.packList.select(this.selectedPackName);
            }
        }

        if (this.optionList != null) {
            if (this.navigation != null) {
                this.optionList.updateNavigation(this.navigation);
            }
            this.optionList.applyShaderPack(this.currentPack, this.workingOptionValues);
            this.optionList.setPackOptions(this.currentPack != null ? this.currentPack.getProperties() : ShaderProperties.empty());
            this.optionList.refresh();
        }

        if (this.navigation != null && this.optionList != null) {
            this.navigation.setActiveOptionList(this.optionList);
            this.navigation.refresh();
        }

        syncShaderToggleWithConfig();
        refreshScreenSwitchButton();
        layoutLists();
    }

    String getAppliedPackName() {
        return this.currentPack != null ? this.currentPack.getName() : null;
    }

    String getSelectedPackName() {
        return this.selectedPackName;
    }

    private void loadShaderPack(String packName) {
        try {
            this.currentPack = ShaderPackLoader.load(packName);
        } catch (IOException exception) {
            Oculus.LOGGER.error("Failed to load shader pack {}", packName, exception);
            this.currentPack = ShaderPack.of(
                packName,
                net.oculus.shaderpack.option.menu.OptionMenuContainer.EMPTY,
                ShaderProperties.empty(),
                ShaderPack.createEmptyOptionValues(),
                AbsolutePackPath.fromAbsolutePath("/"),
                path -> null,
                new EnumMap<>(TextureStage.class),
                null,
                IdMap.empty(),
                Object2IntMaps.emptyMap(),
                net.oculus.shaderpack.option.ProfileSet.empty()
            );
            displayNotification(new TextComponentString("Failed to load shader pack: " + packName));
        }

        refreshWorkingOptionValuesFromCurrentPack();
        this.navigation = new NavigationController(this.currentPack.getMenuContainer());
        this.appliedThisSession = false;
    }

    void refreshScreenSwitchButton() {
        if (this.screenSwitchButton != null) {
            this.screenSwitchButton.displayString = this.optionMenuOpen
                ? I18n.format("options.iris.shaderPackList")
                : I18n.format("options.iris.shaderPackSettings");
            ShaderPackSelectionList.TopButtonRowEntry topRow = this.packList != null ? this.packList.getTopButtonRow() : null;
            boolean shadersEnabled = topRow != null && topRow.shadersEnabled;
            this.screenSwitchButton.enabled = this.optionMenuOpen || shadersEnabled;
        }
    }

    public void markPendingChanges() {
        this.appliedThisSession = false;
    }

    private void layoutLists() {
        int usableWidth = Math.max(1, this.width - LAYOUT_MARGIN * 2);
        if (this.packList != null) {
            int packWidth = Math.min(PACK_LIST_MAX_WIDTH, usableWidth);
            int packLeft = (this.width - packWidth) / 2;
            this.packList.layout(packLeft, packWidth);
        }
        if (this.optionList != null) {
            int optionWidth = Math.min(OPTION_LIST_MAX_WIDTH, usableWidth);
            int optionLeft = (this.width - optionWidth) / 2;
            this.optionList.layout(optionLeft, optionWidth);
        }
    }

    private void drawCommentPanel() {
        int lines = Math.max(1, this.hoveredElementCommentBody.size());
        int panelHeight = Math.max(50, 18 + lines * 10);
        int usableWidth = Math.max(1, this.width - LAYOUT_MARGIN * 2);
        int panelWidth = Math.min(COMMENT_PANEL_WIDTH, Math.max(120, usableWidth));
        panelWidth = Math.min(panelWidth, Math.max(120, this.width - 4));
        int x = (this.width - panelWidth) / 2;
        int y = this.height - panelHeight - 8;

        Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0xC0101010);
        Gui.drawRect(x, y, x + panelWidth, y + 1, 0xFF4F4F4F);
        Gui.drawRect(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF4F4F4F);

        int textY = y + 4;
        if (this.hoveredElementCommentTitle.isPresent()) {
            this.fontRenderer.drawStringWithShadow(this.hoveredElementCommentTitle.get().getFormattedText(), x + 4, textY, 0xFFFFFF);
            textY += 12;
        }

        for (String line : this.hoveredElementCommentBody) {
            this.fontRenderer.drawStringWithShadow(line, x + 4, textY, 0xFFFFFF);
            textY += 10;
        }
    }

    private static ITextComponent createSubtitle(String key) {
        TextComponentTranslation component = new TextComponentTranslation(key);
        Style style = component.getStyle();
        if (style == null) {
            style = new Style();
            component.setStyle(style);
        }
        style.setColor(TextFormatting.GRAY);
        style.setItalic(true);
        return component;
    }

    private String determineInitialSelection(ShaderPack activePack) {
        if (this.config != null && this.config.getSelectedPackName() != null) {
            return this.config.getSelectedPackName();
        }

        if (activePack != null && !activePack.isInternal()) {
            return activePack.getName();
        }

        return null;
    }

    private ShaderPack preloadSelectedPack(ShaderPack fallback) {
        if (this.selectedPackName == null || (fallback != null && this.selectedPackName.equals(fallback.getName()))) {
            return fallback;
        }

        try {
            return ShaderPackLoader.load(this.selectedPackName);
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to load shader pack {} referenced by config", this.selectedPackName, exception);
            displayNotification(new TextComponentString("Missing shader pack: " + this.selectedPackName));
            clearOverridesForPack(this.selectedPackName);
            this.selectedPackName = null;
            persistSelectedPack(true);
            return fallback != null ? fallback : ShaderPackLoader.internalPack();
        }
    }

    private void syncShaderToggleWithConfig() {
        boolean enabledFromConfig;
        if (this.config != null) {
            enabledFromConfig = this.config.areShadersEnabled();
        } else {
            enabledFromConfig = this.currentPack != null && !this.currentPack.isInternal();
        }

        this.pendingShadersEnabled = enabledFromConfig;

        if (this.packList != null) {
            ShaderPackSelectionList.TopButtonRowEntry topRow = this.packList.getTopButtonRow();
            if (topRow != null) {
                topRow.shadersEnabled = enabledFromConfig;
            }
        }
    }

    private void persistSelectedPack(boolean save) {
        if (this.config == null) {
            return;
        }

        this.config.setSelectedPackName(this.selectedPackName);
        if (save) {
            saveConfig("selection");
        }
    }

    private void updateConfigAfterApply(boolean shadersEnabled) {
        if (this.config == null) {
            return;
        }

        if (this.currentPack != null && !this.currentPack.isInternal()) {
            Map<String, String> snapshot = new HashMap<>(this.workingOptionValues.asMap());
            this.config.setOptionOverrides(this.currentPack.getName(), snapshot);
        } else if (this.selectedPackName != null) {
            this.config.clearOptionOverrides(this.selectedPackName);
        }

        this.config.setShadersEnabled(shadersEnabled);
        this.config.setSelectedPackName(this.selectedPackName);
        saveConfig("apply");
    }

    void onShadersToggleChanged(boolean enabled) {
        this.pendingShadersEnabled = enabled;
        markPendingChanges();
        refreshScreenSwitchButton();
    }

    private void saveConfig(String reason) {
        if (this.config == null) {
            return;
        }

        try {
            this.config.save();
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to persist Oculus config ({})", reason, exception);
        }
    }

    private void refreshWorkingOptionValuesFromCurrentPack() {
        refreshWorkingOptionValuesFromCurrentPack(false);
    }

    private void refreshWorkingOptionValuesFromCurrentPack(boolean rebuildOptionList) {
        if (this.currentPack != null && this.currentPack.getOptionValues() != null) {
            this.workingOptionValues = this.currentPack.getOptionValues().mutableCopy();
        } else {
            this.workingOptionValues = ShaderPack.createEmptyOptionValues();
        }

        applyStoredOverridesToWorkingCopy();

        if (rebuildOptionList && this.optionList != null) {
            this.optionList.setOptionValues(this.workingOptionValues);
            this.optionList.refresh();
        }
    }

    private void applyStoredOverridesToWorkingCopy() {
        if (this.config == null || this.workingOptionValues == null) {
            return;
        }

        String packName = this.currentPack != null ? this.currentPack.getName() : null;
        Map<String, String> overrides = this.config.getOptionOverrides(packName);
        if (overrides.isEmpty()) {
            return;
        }

        this.workingOptionValues.addAll(overrides);
    }

    private void captureBaselineState(String reason) {
        this.baselinePack = this.currentPack;
        this.baselinePackName = this.currentPack != null ? this.currentPack.getName() : null;
        this.baselineShadersEnabled = this.pendingShadersEnabled;
        this.baselineOptionValues = this.workingOptionValues != null
            ? this.workingOptionValues.mutableCopy()
            : ShaderPack.createEmptyOptionValues();

        if (this.config != null) {
            this.baselineOptionOverrides = deepCopyOverrides(this.config.getShaderOptionOverrides());
        } else {
            this.baselineOptionOverrides = new HashMap<>();
        }

        Oculus.LOGGER.debug("Captured shader pack baseline via {} for pack {}", reason, this.baselinePackName);
    }

    private Map<String, Map<String, String>> deepCopyOverrides(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new HashMap<>();
        if (source == null) {
            return copy;
        }

        source.forEach((pack, overrides) -> copy.put(pack, new HashMap<>(overrides)));
        return copy;
    }

    private ShaderPack resolveBaselinePack() {
        if (this.baselinePack != null) {
            return this.baselinePack;
        }

        if (this.baselinePackName != null) {
            try {
                return ShaderPackLoader.load(this.baselinePackName);
            } catch (IOException exception) {
                Oculus.LOGGER.warn("Failed to reload baseline pack {} during discard", this.baselinePackName, exception);
            }
        }

        return ShaderPackLoader.internalPack();
    }

    private void clearOverridesForPack(String packName) {
        if (this.config == null || packName == null) {
            return;
        }

        this.config.clearOptionOverrides(packName);
    }

    private boolean validateImportPreconditions() {
        if (!isExternalPackSelected()) {
            displayNotification(coloredMessage(
                "options.iris.importSettings.noPack",
                "Select a shader pack before importing settings.",
                TextFormatting.RED
            ));
            return false;
        }

        if (isFullscreenActive()) {
            displayNotification(coloredMessage(
                "options.iris.mustDisableFullscreen",
                "Please disable fullscreen first!",
                TextFormatting.RED
            ));
            return false;
        }

        return true;
    }

    private boolean validateExportPreconditions() {
        if (!validateImportPreconditions()) {
            return false;
        }

        if (this.workingOptionValues == null || this.workingOptionValues.asMap().isEmpty()) {
            displayNotification(coloredMessage(
                "options.iris.exportSettings.noOptions",
                "No shader settings are available to export.",
                TextFormatting.GRAY
            ));
            return false;
        }

        return true;
    }

    private boolean isExternalPackSelected() {
        return this.currentPack != null && !this.currentPack.isInternal();
    }

    private boolean isFullscreenActive() {
        Minecraft minecraft = this.mc != null ? this.mc : Minecraft.getMinecraft();
        return minecraft != null && minecraft.isFullScreen();
    }

    private Path buildDefaultSettingsPath(String packName) {
        Minecraft minecraft = this.mc != null ? this.mc : Minecraft.getMinecraft();
        Path shaderpacksDir;
        if (minecraft != null && minecraft.gameDir != null) {
            shaderpacksDir = minecraft.gameDir.toPath().resolve("shaderpacks");
        } else {
            shaderpacksDir = Paths.get("shaderpacks");
        }

        String safeName = packName != null && !packName.isEmpty() ? packName : "shaderpack";
        return shaderpacksDir.resolve(safeName + ".txt");
    }

    private ImportFileResult loadImportFile(Path path) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
            return ImportFileResult.success(path, properties);
        } catch (Exception exception) {
            return ImportFileResult.failure(path, exception);
        }
    }

    private void handleImportResult(ImportFileResult result, Throwable throwable) {
        if (throwable != null) {
            Oculus.LOGGER.error("Unexpected error while selecting shader settings to import", throwable);
            displayNotification(coloredMessage(
                "options.iris.importSettings.failed",
                "Failed to import shader settings. Check logs.",
                TextFormatting.RED
            ));
            return;
        }

        if (result == null || result.skipped) {
            Oculus.LOGGER.debug("Shader settings import cancelled or no file selected");
            return;
        }

        if (result.error != null) {
            Oculus.LOGGER.error("Failed to load shader settings from {}", result.path, result.error);
            displayNotification(coloredMessage(
                "options.iris.importSettings.failed",
                "Failed to import settings from %s. Check logs.",
                TextFormatting.RED,
                safeFileName(result.path)
            ));
            return;
        }

        Properties properties = result.properties;
        if (properties == null || properties.isEmpty()) {
            Oculus.LOGGER.error("Shader settings file {} contained no values", result.path);
            displayNotification(coloredMessage(
                "options.iris.importSettings.empty",
                "No shader settings found in %s.",
                TextFormatting.RED,
                safeFileName(result.path)
            ));
            return;
        }

        Map<String, String> values = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }

        int changed = applyImportedValues(values);
        if (changed <= 0) {
            Oculus.LOGGER.info("Imported {} shader settings from {} but no changes were applied", values.size(), result.path);
            displayNotification(coloredMessage(
                "options.iris.importSettings.noChanges",
                "Loaded %s settings from %s but nothing changed.",
                TextFormatting.YELLOW,
                values.size(),
                safeFileName(result.path)
            ));
            return;
        }

        Oculus.LOGGER.info("Imported {} shader settings from {}", changed, result.path);
        displayNotification(coloredMessage(
            "options.iris.importSettings.success",
            "Imported %s settings from %s.",
            TextFormatting.GREEN,
            changed,
            safeFileName(result.path)
        ));
    }

    private int applyImportedValues(Map<String, String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return 0;
        }

        if (this.workingOptionValues == null) {
            this.workingOptionValues = ShaderPack.createEmptyOptionValues();
        }

        Map<String, String> sanitized = new HashMap<>();
        Map<String, String> currentValues = new HashMap<>(this.workingOptionValues.asMap());
        int changed = 0;

        for (Map.Entry<String, String> entry : rawValues.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            sanitized.put(key, value);
            String existing = currentValues.get(key);
            if (!Objects.equals(existing, value)) {
                changed++;
            }
        }

        if (sanitized.isEmpty()) {
            return 0;
        }

        this.workingOptionValues.addAll(sanitized);

        if (changed > 0) {
            markPendingChanges();
        }

        if (this.optionList != null) {
            this.optionList.setOptionValues(this.workingOptionValues);
        }
        if (this.navigation != null) {
            this.navigation.refresh();
        }
        if (this.optionList != null) {
            this.optionList.refresh();
        }

        return changed;
    }

    public void applyProfile(Profile profile) {
        if (profile == null || profile.optionValues == null || profile.optionValues.isEmpty()) {
            return;
        }

        if (this.workingOptionValues == null) {
            this.workingOptionValues = ShaderPack.createEmptyOptionValues();
        }

        Map<String, String> currentValues = new HashMap<>(this.workingOptionValues.asMap());
        int changed = 0;

        for (Map.Entry<String, String> entry : profile.optionValues.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }

            String existing = currentValues.get(key);
            if (!Objects.equals(existing, value)) {
                changed++;
            }
        }

        this.workingOptionValues.addAll(profile.optionValues);

        if (changed > 0) {
            markPendingChanges();
        }

        if (this.optionList != null) {
            this.optionList.setOptionValues(this.workingOptionValues);
            this.optionList.refresh();
        }

        if (this.navigation != null) {
            this.navigation.refresh();
        }

        String profileName = profile.name != null && !profile.name.isEmpty() ? profile.name : I18n.format("options.iris.profile");
        ITextComponent message;
        if (changed > 0) {
            message = GuiUtil.translateOrDefault(
                new TextComponentString(String.format("Queued profile: %s", profileName)),
                "options.iris.profile.apply.success",
                profileName
            );
        } else {
            message = GuiUtil.translateOrDefault(
                new TextComponentString(String.format("Profile %s is already active", profileName)),
                "options.iris.profile.apply.noChanges",
                profileName
            );
        }

        displayNotification(message);
    }

    private ExportFileResult writeExportFile(Path path, Map<String, String> values) {
        Properties properties = new Properties();
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                properties.setProperty(key, value);
            }
        });

        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Oculus Shader Pack Settings");
            }

            return ExportFileResult.success(path, properties.size());
        } catch (Exception exception) {
            return ExportFileResult.failure(path, exception);
        }
    }

    private void handleExportResult(ExportFileResult result, Throwable throwable) {
        if (throwable != null) {
            Oculus.LOGGER.error("Unexpected error while selecting shader settings export destination", throwable);
            displayNotification(coloredMessage(
                "options.iris.exportSettings.failed",
                "Failed to export shader settings. Check logs.",
                TextFormatting.RED
            ));
            return;
        }

        if (result == null || result.skipped) {
            Oculus.LOGGER.debug("Shader settings export cancelled or no destination selected");
            return;
        }

        if (result.error != null) {
            Oculus.LOGGER.error("Failed to export shader settings to {}", result.path, result.error);
            displayNotification(coloredMessage(
                "options.iris.exportSettings.failed",
                "Failed to export settings to %s. Check logs.",
                TextFormatting.RED,
                safeFileName(result.path)
            ));
            return;
        }

        if (result.savedEntries <= 0) {
            Oculus.LOGGER.warn("Exported shader settings to {} but no entries were written", result.path);
            displayNotification(coloredMessage(
                "options.iris.exportSettings.empty",
                "No shader settings were saved to %s.",
                TextFormatting.YELLOW,
                safeFileName(result.path)
            ));
            return;
        }

        Oculus.LOGGER.info("Exported {} shader settings to {}", result.savedEntries, result.path);
        displayNotification(coloredMessage(
            "options.iris.exportSettings.success",
            "Saved %s settings to %s.",
            TextFormatting.GREEN,
            result.savedEntries,
            safeFileName(result.path)
        ));
    }

    private ITextComponent coloredMessage(String translationKey, String fallback, TextFormatting color, Object... args) {
        ITextComponent component = GuiUtil.translateOrDefault(
            new TextComponentString(String.format(fallback, args)),
            translationKey,
            args
        );
        Style style = component.getStyle();
        if (style == null) {
            style = new Style();
            component.setStyle(style);
        }
        style.setColor(color);
        return component;
    }

    private String safeFileName(Path path) {
        if (path == null) {
            return "file";
        }

        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }

    private void scheduleOnClient(Runnable runnable) {
        Minecraft minecraft = this.mc != null ? this.mc : Minecraft.getMinecraft();
        if (minecraft != null) {
            minecraft.addScheduledTask(runnable);
        } else {
            runnable.run();
        }
    }

    private static final class ImportFileResult {
        private final Path path;
        private final Properties properties;
        private final Exception error;
        private final boolean skipped;

        private ImportFileResult(Path path, Properties properties, Exception error, boolean skipped) {
            this.path = path;
            this.properties = properties;
            this.error = error;
            this.skipped = skipped;
        }

        private static ImportFileResult success(Path path, Properties properties) {
            return new ImportFileResult(path, properties, null, false);
        }

        private static ImportFileResult failure(Path path, Exception error) {
            return new ImportFileResult(path, null, error, false);
        }

        private static ImportFileResult skipped() {
            return new ImportFileResult(null, null, null, true);
        }
    }

    private static final class ExportFileResult {
        private final Path path;
        private final Exception error;
        private final int savedEntries;
        private final boolean skipped;

        private ExportFileResult(Path path, Exception error, int savedEntries, boolean skipped) {
            this.path = path;
            this.error = error;
            this.savedEntries = savedEntries;
            this.skipped = skipped;
        }

        private static ExportFileResult success(Path path, int savedEntries) {
            return new ExportFileResult(path, null, savedEntries, false);
        }

        private static ExportFileResult failure(Path path, Exception error) {
            return new ExportFileResult(path, error, 0, false);
        }

        private static ExportFileResult skipped() {
            return new ExportFileResult(null, null, 0, true);
        }
    }
}

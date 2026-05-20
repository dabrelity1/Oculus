package net.oculus.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ShaderPackScreenSourceTest {
    @Test
    public void selectingPackDoesNotPersistUntilApply() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String selectionBody = methodBody(source, "void onShaderPackSelected(String packName)");

        assertTrue(selectionBody.contains("this.selectedPackName = packName;"));
        assertTrue(selectionBody.contains("loadShaderPack(packName);"));
        assertFalse(selectionBody.contains("persistSelectedPack(true);"));
        assertFalse(selectionBody.contains("saveConfig("));

        String applyBody = methodBody(source, "private boolean updateConfigAfterApply(boolean shadersEnabled)");
        assertTrue(applyBody.contains("this.config.setSelectedPackName(this.selectedPackName);"));
        assertTrue(applyBody.contains("if (!saveConfig(\"apply\"))"));
    }

    @Test
    public void pendingShaderToggleSurvivesSelectionRefresh() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String syncBody = methodBody(source, "private void syncShaderToggleWithConfig()");

        assertTrue(syncBody.contains("this.appliedThisSession ? getAppliedShadersEnabled() : this.pendingShadersEnabled"));
        assertTrue(syncBody.contains("this.pendingShadersEnabled = enabled;"));
        assertTrue(syncBody.contains("topRow.shadersEnabled = enabled;"));
    }

    @Test
    public void clickingPackEnablesShadersBeforeSelectionRefresh() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackSelectionList.java");
        String clickBody = methodBodyAfter(source, "private class ShaderPackEntry",
            "public void click(int slotIndex, int mouseX, int mouseY, boolean isDoubleClick)");

        int enableIndex = clickBody.indexOf("topButtonRow.setShadersEnabled(true);");
        int selectIndex = clickBody.indexOf("screen.onShaderPackSelected(this.packName);");
        assertTrue(enableIndex >= 0);
        assertTrue(selectIndex >= 0);
        assertTrue(enableIndex < selectIndex);
    }

    @Test
    public void keyboardPackSelectionEnablesShadersBeforeSelectionRefresh() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackSelectionList.java");
        String moveSelectionBody = methodBody(source, "private void moveSelection(int direction)");

        int enableIndex = moveSelectionBody.indexOf("topButtonRow.setShadersEnabled(true);");
        int selectIndex = moveSelectionBody.indexOf("selectEntry((ShaderPackEntry) entry);");
        int refreshIndex = moveSelectionBody.indexOf("screen.onShaderPackSelected(((ShaderPackEntry) entry).packName);");

        assertTrue(enableIndex >= 0);
        assertTrue(selectIndex >= 0);
        assertTrue(refreshIndex >= 0);
        assertTrue(enableIndex < selectIndex);
        assertTrue(selectIndex < refreshIndex);
    }

    @Test
    public void shaderPackListHandlesUnexpectedDirectoryEnumerationFailuresLikeReference() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackSelectionList.java");
        String refreshBody = methodBody(source, "public void refresh()");

        assertFalse(source.contains("import java.io.IOException;"));
        assertTrue(refreshBody.contains("names = ShaderpackDirectoryManager.findShaderPacks();"));
        assertTrue(refreshBody.contains("} catch (Throwable throwable) {"));
        assertTrue(refreshBody.contains("Oculus.LOGGER.error(\"Error reading shaderpacks directory\", throwable);"));
        assertTrue(refreshBody.contains("addErrorMessage();"));
        assertTrue(refreshBody.contains("return;"));
    }

    @Test
    public void appliedPackMarkerUsesBaselineWhileChangesArePending() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String appliedBody = methodBody(source, "String getAppliedPackName()");

        assertTrue(appliedBody.contains("if (!this.appliedThisSession)"));
        assertTrue(appliedBody.contains("return this.baselinePackName;"));
        assertTrue(appliedBody.contains("return this.currentPack != null ? this.currentPack.getName() : null;"));
    }

    @Test
    public void applyChangesPersistsConfigBeforeSharedReload() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String applyBody = methodBody(source, "private boolean applyChanges()");

        int packValidationIndex = applyBody.indexOf("packToApply = reloadSelectedPackForApply(selectedPack.getName(), this.workingOptionValues);");
        int currentPackIndex = applyBody.indexOf("this.currentPack = packToApply;");
        int pendingIndex = applyBody.indexOf("this.pendingShadersEnabled = enableShaders;");
        int configIndex = applyBody.indexOf("if (!updateConfigAfterApply(enableShaders))");
        int reloadIndex = applyBody.indexOf("boolean reloaded = ShaderPackReloader.reload();");

        assertTrue(packValidationIndex >= 0);
        assertTrue(currentPackIndex >= 0);
        assertTrue(pendingIndex >= 0);
        assertTrue(configIndex >= 0);
        assertTrue(reloadIndex >= 0);
        assertTrue(packValidationIndex < configIndex);
        assertTrue(pendingIndex < configIndex);
        assertTrue(configIndex < reloadIndex);
        assertTrue(reloadIndex < currentPackIndex);
        assertFalse(applyBody.contains("PipelineManager.INSTANCE.reloadShaderPack("));
    }

    @Test
    public void failedConfigSaveStopsApplyBeforeSharedReload() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String applyBody = methodBody(source, "private boolean applyChanges()");
        String updateBody = methodBody(source, "private boolean updateConfigAfterApply(boolean shadersEnabled)");
        String saveBody = methodBody(source, "private boolean saveConfig(String reason)");

        int configFailureIndex = applyBody.indexOf("if (!updateConfigAfterApply(enableShaders))");
        int reloadIndex = applyBody.indexOf("boolean reloaded = ShaderPackReloader.reload();");
        int returnFalseIndex = applyBody.indexOf("return false;", configFailureIndex);
        int currentPackIndex = applyBody.indexOf("this.currentPack = packToApply;");

        assertTrue(configFailureIndex >= 0);
        assertTrue(reloadIndex >= 0);
        assertTrue(returnFalseIndex > configFailureIndex);
        assertTrue(returnFalseIndex < reloadIndex);
        assertTrue(currentPackIndex > reloadIndex);
        assertTrue(updateBody.contains("ConfigSnapshot snapshot = ConfigSnapshot.capture(this.config);"));
        assertTrue(updateBody.contains("if (!saveConfig(\"apply\"))"));
        assertTrue(updateBody.contains("snapshot.restore(this.config);"));
        assertTrue(saveBody.contains("return true;"));
        assertTrue(saveBody.contains("return false;"));
    }

    @Test
    public void failedSharedReloadDoesNotMarkPendingPackApplied() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String applyBody = methodBody(source, "private boolean applyChanges()");

        int snapshotIndex = applyBody.indexOf("ConfigSnapshot applyConfigSnapshot = this.config != null ? ConfigSnapshot.capture(this.config) : null;");
        int configIndex = applyBody.indexOf("if (!updateConfigAfterApply(enableShaders))");
        int reloadIndex = applyBody.indexOf("boolean reloaded = ShaderPackReloader.reload();");
        int failureIndex = applyBody.indexOf("if (enableShaders && !reloaded)");
        int unappliedIndex = applyBody.indexOf("this.appliedThisSession = false;", failureIndex);
        int restoreConfigIndex = applyBody.indexOf("restoreConfigAfterApplyReloadFailure(applyConfigSnapshot);", unappliedIndex);
        int notificationIndex = applyBody.indexOf("Failed to apply shader pack. Check logs.", failureIndex);
        int refreshIndex = applyBody.indexOf("refreshForChangedPack();", failureIndex);
        int returnFalseIndex = applyBody.indexOf("return false;", failureIndex);
        int markAppliedIndex = applyBody.indexOf("this.packList.markAppliedPack(this.selectedPackName);", failureIndex);
        int currentPackIndex = applyBody.indexOf("this.currentPack = packToApply;", failureIndex);
        int captureIndex = applyBody.indexOf("captureBaselineState(\"apply\");", failureIndex);
        String restoreBody = methodBody(source, "private void restoreConfigAfterApplyReloadFailure(ConfigSnapshot snapshot)");

        assertTrue(snapshotIndex >= 0);
        assertTrue(configIndex > snapshotIndex);
        assertTrue(reloadIndex >= 0);
        assertTrue(reloadIndex > configIndex);
        assertTrue(failureIndex > reloadIndex);
        assertTrue(unappliedIndex > failureIndex);
        assertTrue(restoreConfigIndex > unappliedIndex);
        assertTrue(notificationIndex > restoreConfigIndex);
        assertTrue(refreshIndex > notificationIndex);
        assertTrue(returnFalseIndex > refreshIndex);
        assertTrue(markAppliedIndex > returnFalseIndex);
        assertTrue(currentPackIndex > returnFalseIndex);
        assertTrue(captureIndex > returnFalseIndex);
        assertTrue(restoreBody.contains("if (this.config == null || snapshot == null)"));
        assertTrue(restoreBody.contains("snapshot.restore(this.config);"));
        assertTrue(restoreBody.contains("if (!saveConfig(\"apply rollback\"))"));
        assertTrue(restoreBody.contains("displayConfigSaveFailure();"));
    }

    @Test
    public void failedSharedReloadRestoresPreviousRuntimeAfterPersistedRollback() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String restoreBody = methodBody(source, "private void restoreConfigAfterApplyReloadFailure(ConfigSnapshot snapshot)");

        int restoreIndex = restoreBody.indexOf("snapshot.restore(this.config);");
        int saveIndex = restoreBody.indexOf("if (!saveConfig(\"apply rollback\"))", restoreIndex);
        int notifyIndex = restoreBody.indexOf("displayConfigSaveFailure();", saveIndex);
        int saveFailureReturnIndex = restoreBody.indexOf("return;", notifyIndex);
        int reloadIndex = restoreBody.indexOf("boolean restored = ShaderPackReloader.reload();", saveFailureReturnIndex);
        int warningGuardIndex = restoreBody.indexOf(
            "this.config.areShadersEnabled() && this.config.getSelectedPackName() != null && !restored",
            reloadIndex);

        assertTrue(restoreIndex >= 0);
        assertTrue(saveIndex > restoreIndex);
        assertTrue(notifyIndex > saveIndex);
        assertTrue(saveFailureReturnIndex > notifyIndex);
        assertTrue(reloadIndex > saveFailureReturnIndex);
        assertTrue(warningGuardIndex > reloadIndex);
    }

    @Test
    public void doneButtonOnlyClosesAfterSuccessfulApply() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String actionBody = methodBody(source, "protected void actionPerformed(GuiButton button)");

        int doneIndex = actionBody.indexOf("case 0: // Done");
        int applyGuardIndex = actionBody.indexOf("if (applyChanges())", doneIndex);
        int closeIndex = actionBody.indexOf("this.mc.displayGuiScreen(this.parent);", applyGuardIndex);
        int breakIndex = actionBody.indexOf("break;", closeIndex);

        assertTrue(doneIndex >= 0);
        assertTrue(applyGuardIndex > doneIndex);
        assertTrue(closeIndex > applyGuardIndex);
        assertTrue(breakIndex > closeIndex);
    }

    @Test
    public void cancelButtonOnlyClosesAfterSuccessfulDiscardPersistence() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String dropBody = methodBody(source, "private void dropChangesAndClose()");

        int markDropChanges = dropBody.indexOf("this.dropChanges = true;");
        int discardGuard = dropBody.indexOf("if (discardChanges())", markDropChanges);
        int closeScreen = dropBody.indexOf("this.mc.displayGuiScreen(this.parent);", discardGuard);

        assertTrue(markDropChanges >= 0);
        assertTrue(discardGuard > markDropChanges);
        assertTrue("Cancel must keep the shader screen open when baseline discard cannot be persisted",
            closeScreen > discardGuard);
    }

    @Test
    public void failedDiscardSaveRestoresSnapshotBeforePublishingBaselineUiState() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String discardBody = methodBody(source, "private boolean discardChanges()");
        String restoreBody = methodBody(source, "private void restoreBaselineConfig()");

        int packToRestore = discardBody.indexOf("ShaderPack packToRestore = resolveBaselinePack();");
        int optionsToRestore = discardBody.indexOf("MutableOptionValues optionValuesToRestore = this.baselineOptionValues != null",
            packToRestore);
        int snapshot = discardBody.indexOf("ConfigSnapshot snapshot = ConfigSnapshot.capture(this.config);",
            optionsToRestore);
        int restoreBaseline = discardBody.indexOf("restoreBaselineConfig();", snapshot);
        int save = discardBody.indexOf("if (!saveConfig(\"discard\"))", restoreBaseline);
        int restoreSnapshot = discardBody.indexOf("snapshot.restore(this.config);", save);
        int notifyFailure = discardBody.indexOf("displayConfigSaveFailure();", restoreSnapshot);
        int refreshPending = discardBody.indexOf("refreshForChangedPack();", notifyFailure);
        int returnFalse = discardBody.indexOf("return false;", refreshPending);
        int clearDropFlag = discardBody.indexOf("this.dropChanges = false;", returnFalse);
        int markApplied = discardBody.indexOf("this.appliedThisSession = true;", clearDropFlag);
        int publishPack = discardBody.indexOf("this.currentPack = packToRestore;", markApplied);
        int publishSelection = discardBody.indexOf("this.selectedPackName = this.baselinePackName;", publishPack);
        int publishEnabled = discardBody.indexOf("this.pendingShadersEnabled = this.baselineShadersEnabled;",
            publishSelection);
        int publishOptions = discardBody.indexOf("this.workingOptionValues = optionValuesToRestore;", publishEnabled);
        int returnTrue = discardBody.indexOf("return true;", publishOptions);

        assertTrue(packToRestore >= 0);
        assertTrue(optionsToRestore > packToRestore);
        assertTrue(snapshot > optionsToRestore);
        assertTrue(restoreBaseline > snapshot);
        assertTrue(save > restoreBaseline);
        assertTrue(restoreSnapshot > save);
        assertTrue(notifyFailure > restoreSnapshot);
        assertTrue(refreshPending > notifyFailure);
        assertTrue(returnFalse > refreshPending);
        assertTrue("Discard must not publish baseline GUI state until config persistence succeeds",
            clearDropFlag > returnFalse);
        assertTrue(markApplied > clearDropFlag);
        assertTrue(publishPack > markApplied);
        assertTrue(publishSelection > publishPack);
        assertTrue(publishEnabled > publishSelection);
        assertTrue(publishOptions > publishEnabled);
        assertTrue(returnTrue > publishOptions);

        assertTrue(restoreBody.contains("this.config.clearShaderOptionOverrides();"));
        assertTrue(restoreBody.contains(
            "this.baselineOptionOverrides.forEach((pack, overrides) -> this.config.setOptionOverrides(pack, overrides));"));
        assertTrue(restoreBody.contains("this.config.setSelectedPackName(this.baselinePackName);"));
        assertTrue(restoreBody.contains("this.config.setShadersEnabled(this.baselineShadersEnabled);"));
    }

    @Test
    public void invalidStoredSelectionClearsOverridesAndPersistsRecovery() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String preloadBody = methodBody(source, "private ShaderPack preloadSelectedPack(ShaderPack fallback)");
        String persistBody = methodBody(source, "private boolean persistSelectedPack(boolean save)");

        int packName = preloadBody.indexOf("String packName = this.selectedPackName;");
        int load = preloadBody.indexOf("return ShaderPackLoader.load(packName, getStoredOverrides(packName));", packName);
        int notify = preloadBody.indexOf("Missing or invalid shader pack: \" + packName", load);
        int snapshot = preloadBody.indexOf("ConfigSnapshot snapshot = this.config != null ? ConfigSnapshot.capture(this.config) : null;",
            notify);
        int clear = preloadBody.indexOf("clearOverridesForPack(packName);", snapshot);
        int clearSelection = preloadBody.indexOf("this.selectedPackName = null;", clear);
        int persist = preloadBody.indexOf("if (!persistSelectedPack(true))", clearSelection);
        int restoreConfig = preloadBody.indexOf("snapshot.restore(this.config);", persist);
        int restoreSelection = preloadBody.indexOf("this.selectedPackName = packName;", restoreConfig);
        int notifySaveFailure = preloadBody.indexOf("displayConfigSaveFailure();", restoreSelection);
        int fallback = preloadBody.indexOf("return fallback != null ? fallback : ShaderPackLoader.internalPack();",
            notifySaveFailure);

        assertTrue(source.contains("import java.util.zip.ZipError;"));
        assertTrue(preloadBody.contains("} catch (Exception | ZipError exception) {"));
        assertTrue(packName >= 0);
        assertTrue(load > packName);
        assertTrue(notify > load);
        assertTrue(snapshot > notify);
        assertTrue(clear > snapshot);
        assertTrue(clearSelection > clear);
        assertTrue(persist > clearSelection);
        assertTrue(restoreConfig > persist);
        assertTrue(restoreSelection > restoreConfig);
        assertTrue(notifySaveFailure > restoreSelection);
        assertTrue(fallback > notifySaveFailure);
        assertFalse(preloadBody.contains("clearOverridesForPack(this.selectedPackName);"));
        assertTrue(persistBody.contains("return saveConfig(\"selection\");"));
        assertTrue(persistBody.contains("return true;"));
    }

    @Test
    public void selectedPackLoadTreatsCorruptZipErrorsAsRecoverableLikeReload() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String loadBody = methodBody(source, "private void loadShaderPack(String packName)");

        assertTrue(loadBody.contains("ShaderPackLoader.load(packName, getStoredOverrides(packName));"));
        assertTrue(loadBody.contains("} catch (Exception | ZipError exception) {"));
        assertTrue(loadBody.contains("Failed to load shader pack"));
        assertTrue(loadBody.contains("displayNotification(new TextComponentString(\"Failed to load shader pack: \" + packName));"));
        assertTrue(loadBody.contains("this.appliedThisSession = false;"));
    }

    @Test
    public void applyAndDiscardReloadBoundariesTreatCorruptZipErrorsAsRecoverable() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String applyReloadBody = methodBody(source,
            "private ShaderPack reloadSelectedPackForApply(String packName, MutableOptionValues optionValues)");
        String discardReloadBody = methodBody(source, "private ShaderPack resolveBaselinePack()");

        assertTrue(applyReloadBody.contains("return ShaderPackLoader.load(packName, overrides);"));
        assertTrue(applyReloadBody.contains("} catch (Exception | ZipError exception) {"));
        assertTrue(applyReloadBody.contains("Failed to apply shader options. Check logs."));
        assertTrue(applyReloadBody.contains("return null;"));

        assertTrue(discardReloadBody.contains("return ShaderPackLoader.load(this.baselinePackName,"));
        assertTrue(discardReloadBody.contains("} catch (Exception | ZipError exception) {"));
        assertTrue(discardReloadBody.contains("return ShaderPackLoader.internalPack();"));
    }

    @Test
    public void escapeKeyUsesReferenceApplyCloseBoundary() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String keyBody = methodBody(source, "protected void keyTyped(char typedChar, int keyCode)");

        int escapeIndex = keyBody.indexOf("if (keyCode == Keyboard.KEY_ESCAPE)");
        int hiddenIndex = keyBody.indexOf("this.guiHidden = false;", escapeIndex);
        int historyIndex = keyBody.indexOf("this.navigation != null && this.navigation.hasHistory()", hiddenIndex);
        int backIndex = keyBody.indexOf("this.navigation.back();", historyIndex);
        int optionIndex = keyBody.indexOf("this.optionMenuOpen = false;", backIndex);
        int superIndex = keyBody.indexOf("super.keyTyped(typedChar, keyCode);", optionIndex);

        assertTrue(escapeIndex >= 0);
        assertTrue(hiddenIndex > escapeIndex);
        assertTrue(historyIndex > hiddenIndex);
        assertTrue(backIndex > historyIndex);
        assertTrue(optionIndex > backIndex);
        assertTrue(superIndex > optionIndex);
        assertFalse(keyBody.contains("dropChangesAndClose();"));
    }

    @Test
    public void shaderPackListExposesShadowDistanceAfterColorSpaceControl() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackSelectionList.java");
        String refreshBody = methodBody(source, "public void refresh()");

        int colorSpaceIndex = refreshBody.indexOf("entries.add(new ColorSpaceEntry());");
        int shadowDistanceIndex = refreshBody.indexOf("entries.add(this.shadowDistanceEntry);");

        assertTrue(colorSpaceIndex >= 0);
        assertTrue(refreshBody.contains("this.shadowDistanceEntry = new ShadowDistanceEntry();"));
        assertTrue(shadowDistanceIndex >= 0);
        assertTrue(colorSpaceIndex < shadowDistanceIndex);

        String shadowEntryBody = classBody(source, "private class ShadowDistanceEntry");
        assertTrue(shadowEntryBody.contains("screen.isShadowDistanceControlAvailable()"));
        assertTrue(shadowEntryBody.contains("screen.getShadowDistanceButtonLabel()"));
        assertTrue(shadowEntryBody.contains("screen.getShadowDistanceSliderFraction()"));
        assertTrue(shadowEntryBody.contains("updateFromMouse(mouseX);"));
    }

    @Test
    public void shadowDistanceControlUsesForcedDistanceAndSavesImmediately() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");

        String availabilityBody = methodBody(source, "boolean isShadowDistanceControlAvailable()");
        assertTrue(availabilityBody.contains("this.config != null"));
        assertTrue(availabilityBody.contains("!getForcedShadowDistanceChunks().isPresent()"));

        String displayBody = methodBody(source, "private int getDisplayedShadowDistanceChunks()");
        assertTrue(displayBody.contains("getForcedShadowDistanceChunks().orElse(getConfiguredShadowDistanceChunks())"));

        String setterBody = methodBody(source, "void setShadowDistanceFromSlider(float sliderFraction)");
        int previousIndex = setterBody.indexOf("int previousDistance = this.config.getMaxShadowRenderDistance();");
        int configIndex = setterBody.indexOf("this.config.setMaxShadowRenderDistance(nextDistance);");
        int saveIndex = setterBody.indexOf("if (!saveConfig(\"shadowDistance\"))");

        assertTrue(previousIndex >= 0);
        assertTrue(configIndex >= 0);
        assertTrue(saveIndex >= 0);
        assertTrue(previousIndex < configIndex);
        assertTrue(configIndex < saveIndex);
        assertTrue(setterBody.contains("previousDistance == nextDistance"));
        assertTrue(setterBody.contains("Math.round(clamp(sliderFraction) * MAX_SHADOW_DISTANCE_CHUNKS)"));
    }

    @Test
    public void immediateConfigControlsRestorePreviousValueWhenSaveFails() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String colorBody = methodBody(source, "void cycleColorSpace()");
        String shadowBody = methodBody(source, "void setShadowDistanceFromSlider(float sliderFraction)");

        int colorCaptureIndex = colorBody.indexOf("ColorSpace current = getConfiguredColorSpace();");
        int colorSetIndex = colorBody.indexOf("this.config.setColorSpace(next);");
        int colorSaveIndex = colorBody.indexOf("if (!saveConfig(\"colorSpace\"))");
        int colorRestoreIndex = colorBody.indexOf("this.config.setColorSpace(current);", colorSaveIndex);
        int colorReturnIndex = colorBody.indexOf("return;", colorRestoreIndex);
        int colorSuccessIndex = colorBody.indexOf("displayNotification(new TextComponentString(getColorSpaceButtonLabel()))");

        assertTrue(colorCaptureIndex >= 0);
        assertTrue(colorSetIndex > colorCaptureIndex);
        assertTrue(colorSaveIndex > colorSetIndex);
        assertTrue(colorRestoreIndex > colorSaveIndex);
        assertTrue(colorReturnIndex > colorRestoreIndex);
        assertTrue(colorSuccessIndex > colorReturnIndex);

        int shadowCaptureIndex = shadowBody.indexOf("int previousDistance = this.config.getMaxShadowRenderDistance();");
        int shadowSetIndex = shadowBody.indexOf("this.config.setMaxShadowRenderDistance(nextDistance);");
        int shadowSaveIndex = shadowBody.indexOf("if (!saveConfig(\"shadowDistance\"))");
        int shadowRestoreIndex = shadowBody.indexOf("this.config.setMaxShadowRenderDistance(previousDistance);", shadowSaveIndex);
        int shadowReturnIndex = shadowBody.indexOf("return;", shadowRestoreIndex);
        int shadowSuccessIndex = shadowBody.indexOf("displayNotification(new TextComponentString(getShadowDistanceButtonLabel()))");

        assertTrue(shadowCaptureIndex >= 0);
        assertTrue(shadowSetIndex > shadowCaptureIndex);
        assertTrue(shadowSaveIndex > shadowSetIndex);
        assertTrue(shadowRestoreIndex > shadowSaveIndex);
        assertTrue(shadowReturnIndex > shadowRestoreIndex);
        assertTrue(shadowSuccessIndex > shadowReturnIndex);
        assertTrue(colorBody.contains("displayConfigSaveFailure();"));
        assertTrue(shadowBody.contains("displayConfigSaveFailure();"));
    }

    @Test
    public void resetCurrentPackOptionsStaysPendingUntilApply() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String resetBody = methodBody(source, "public boolean resetCurrentPackOptions()");
        String applyBody = methodBody(source, "private boolean updateConfigAfterApply(boolean shadersEnabled)");

        int clearWorkingIndex = resetBody.indexOf("optionValues.clearAll();");
        int pendingIndex = resetBody.indexOf("markPendingChanges();", clearWorkingIndex);

        assertTrue(clearWorkingIndex >= 0);
        assertTrue(pendingIndex > clearWorkingIndex);
        assertTrue(resetBody.contains("Shader options reset. Press Apply to finish."));
        assertFalse(resetBody.contains("this.config.clearOptionOverrides("));
        assertFalse(resetBody.contains("saveConfig("));
        assertTrue(applyBody.contains("this.config.setOptionOverrides(this.currentPack.getName(), optionSnapshot);"));
    }

    @Test
    public void importedAndProfileOptionsUseEffectiveOptionChanges() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackScreen.java");
        String importBody = methodBody(source, "private int applyImportedValues(Map<String, String> rawValues)");
        String profileBody = methodBody(source, "public void applyProfile(Profile profile)");
        String effectiveBody = methodBody(source, "private int applyEffectiveOptionValues(Map<String, String> rawValues)");
        String countBody = methodBody(source, "private int countEffectiveOptionChanges(Map<String, String> before, Map<String, String> after)");

        assertTrue(importBody.contains("return applyEffectiveOptionValues(rawValues);"));
        assertTrue(profileBody.contains("int changed = applyEffectiveOptionValues(profile.optionValues);"));
        assertFalse(importBody.contains("Objects.equals("));
        assertFalse(profileBody.contains("Objects.equals("));

        int beforeIndex = effectiveBody.indexOf("Map<String, String> before = new HashMap<>(this.workingOptionValues.asMap());");
        int candidateIndex = effectiveBody.indexOf("MutableOptionValues candidate = this.workingOptionValues.mutableCopy();", beforeIndex);
        int addAllIndex = effectiveBody.indexOf("candidate.addAll(sanitized);", candidateIndex);
        int afterIndex = effectiveBody.indexOf("Map<String, String> after = new HashMap<>(candidate.asMap());", addAllIndex);
        int changedIndex = effectiveBody.indexOf("int changed = countEffectiveOptionChanges(before, after);", afterIndex);
        int assignIndex = effectiveBody.indexOf("this.workingOptionValues = candidate;", changedIndex);
        int markIndex = effectiveBody.indexOf("markPendingChanges();", assignIndex);
        int refreshIndex = effectiveBody.indexOf("refreshOptionControlsAfterEffectiveChange();", markIndex);

        assertTrue(beforeIndex >= 0);
        assertTrue(candidateIndex > beforeIndex);
        assertTrue(addAllIndex > candidateIndex);
        assertTrue(afterIndex > addAllIndex);
        assertTrue(changedIndex > afterIndex);
        assertTrue(assignIndex > changedIndex);
        assertTrue(markIndex > assignIndex);
        assertTrue(refreshIndex > markIndex);
        assertTrue(countBody.contains("Set<String> keys = new HashSet<>(before.keySet());"));
        assertTrue(countBody.contains("keys.addAll(after.keySet());"));
        assertTrue(countBody.contains("Objects.equals(before.get(key), after.get(key))"));
    }

    @Test
    public void shadowDistanceSliderDragsUntilMouseRelease() throws Exception {
        String source = readSource("src/main/java/net/oculus/gui/ShaderPackSelectionList.java");
        String listReleaseBody = methodBody(source, "public void mouseReleased(int mouseX, int mouseY, int button)");
        String shadowEntryBody = classBody(source, "private class ShadowDistanceEntry");

        assertTrue(listReleaseBody.contains("this.shadowDistanceEntry.mouseReleased();"));
        assertTrue(shadowEntryBody.contains("private boolean dragging;"));
        assertTrue(shadowEntryBody.contains("this.dragging = true;"));
        assertTrue(shadowEntryBody.contains("this.dragging = false;"));
        assertTrue(shadowEntryBody.contains("if (this.dragging && disabled)"));
        assertTrue(shadowEntryBody.contains("} else if (this.dragging)"));
        assertTrue(shadowEntryBody.contains("updateFromMouse(mouseX);"));
        assertTrue(shadowEntryBody.contains("float fraction = (mouseX - this.lastSliderX) / (float) Math.max(1, this.lastSliderWidth);"));
        assertTrue(shadowEntryBody.contains("screen.setShadowDistanceFromSlider(fraction);"));
    }

    private static String readSource(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method signature " + signature, signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body for " + signature, bodyStart >= 0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, index);
                }
            }
        }

        throw new AssertionError("Unterminated method body for " + signature);
    }

    private static String classBody(String source, String signature) {
        return methodBody(source, signature);
    }

    private static String methodBodyAfter(String source, String anchor, String signature) {
        int anchorIndex = source.indexOf(anchor);
        assertTrue("Missing anchor " + anchor, anchorIndex >= 0);
        return methodBody(source.substring(anchorIndex), signature);
    }
}

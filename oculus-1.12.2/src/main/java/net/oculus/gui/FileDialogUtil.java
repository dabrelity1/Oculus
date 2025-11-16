package net.oculus.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FileDialogUtil {
    private static final Logger LOGGER = LogManager.getLogger();

    private FileDialogUtil() {
    }

    public enum DialogType { SAVE, OPEN }

    public static CompletableFuture<Optional<Path>> fileSelectDialog(
        DialogType dialog, String title, Path origin, String filterLabel, String... filters
    ) {
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();

        Runnable chooserTask = () -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(title != null ? title : "");
                chooser.setMultiSelectionEnabled(false);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

                if (origin != null) {
                    configureOrigin(chooser, origin);
                }

                configureFilters(chooser, filterLabel, filters);

                int result = dialog == DialogType.SAVE
                    ? chooser.showSaveDialog(null)
                    : chooser.showOpenDialog(null);

                Optional<Path> selection = Optional.empty();
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    selection = Optional.of(chooser.getSelectedFile().toPath());
                }

                final Optional<Path> resolvedSelection = selection;
                CompletableFuture.runAsync(() -> future.complete(resolvedSelection));
            } catch (Throwable throwable) {
                LOGGER.error("Failed to open file chooser", throwable);
                CompletableFuture.runAsync(() -> future.completeExceptionally(throwable));
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            chooserTask.run();
        } else {
            SwingUtilities.invokeLater(chooserTask);
        }

        return future;
    }

    private static void configureOrigin(JFileChooser chooser, Path origin) {
        try {
            Path normalized = origin.toAbsolutePath().normalize();
            File originFile = normalized.toFile();

            if (Files.exists(normalized)) {
                if (Files.isDirectory(normalized)) {
                    chooser.setCurrentDirectory(originFile);
                } else {
                    File parent = originFile.getParentFile();
                    if (parent != null && parent.exists()) {
                        chooser.setCurrentDirectory(parent);
                    }
                    chooser.setSelectedFile(originFile);
                }
            } else {
                File parent = originFile.getParentFile();
                if (parent != null && parent.exists()) {
                    chooser.setCurrentDirectory(parent);
                }
                chooser.setSelectedFile(originFile);
            }
        } catch (Exception exception) {
            LOGGER.debug("Failed to configure file chooser origin {}", origin, exception);
        }
    }

    private static void configureFilters(JFileChooser chooser, String filterLabel, String... filters) {
        if (filters == null || filters.length == 0) {
            chooser.setAcceptAllFileFilterUsed(true);
            return;
        }

        String[] extensions = Arrays.stream(filters)
            .map(FileDialogUtil::sanitizeExtension)
            .filter(ext -> !ext.isEmpty())
            .toArray(String[]::new);

        if (extensions.length == 0) {
            chooser.setAcceptAllFileFilterUsed(true);
            return;
        }

        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
            filterLabel != null ? filterLabel : "Files",
            extensions
        ));
    }

    private static String sanitizeExtension(String rawFilter) {
        if (rawFilter == null) {
            return "";
        }

        String trimmed = rawFilter.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("*")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }
}

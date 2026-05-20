package net.oculus.texture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class TextureLifecycleTrackerSourceTest {
    @Test
    public void textureDeletesClearTrackerInfoAndPbrHoldersLikeReference() throws IOException {
        String source = read(Paths.get("src/main/java/net/oculus/texture/TextureLifecycleTracker.java"));

        int positiveGuard = source.indexOf("if (textureId <= 0)");
        int textureTracker = source.indexOf("TextureTracker.INSTANCE.onDeleteTexture(textureId)", positiveGuard);
        int infoCache = source.indexOf("TextureInfoCache.INSTANCE.onDeleteTexture(textureId)", textureTracker);
        int pbrManager = source.indexOf("PBRTextureManager.INSTANCE.onDeleteTexture(textureId)", infoCache);

        assertTrue(positiveGuard >= 0);
        assertTrue(textureTracker > positiveGuard);
        assertTrue(infoCache > textureTracker);
        assertTrue(pbrManager > infoCache);
    }

    @Test
    public void textureDeleteNotificationsAreIsolatedSoCleanupCannotShortCircuit() throws IOException {
        String source = read(Paths.get("src/main/java/net/oculus/texture/TextureLifecycleTracker.java"));
        String deleteBody = methodBody(source, "public static void onDeleteTexture(int textureId)");
        String helperBody = methodBody(source, "private static Throwable runDeleteNotification(Throwable failure, Runnable notification)");

        int failureLocal = deleteBody.indexOf("Throwable failure = null;");
        int textureTracker = deleteBody.indexOf(
            "failure = runDeleteNotification(failure, () -> TextureTracker.INSTANCE.onDeleteTexture(textureId));",
            failureLocal);
        int infoCache = deleteBody.indexOf(
            "failure = runDeleteNotification(failure, () -> TextureInfoCache.INSTANCE.onDeleteTexture(textureId));",
            textureTracker);
        int pbrManager = deleteBody.indexOf(
            "failure = runDeleteNotification(failure, () -> PBRTextureManager.INSTANCE.onDeleteTexture(textureId));",
            infoCache);
        int logFailure = deleteBody.indexOf("if (failure != null)", pbrManager);

        assertTrue(failureLocal >= 0);
        assertTrue(textureTracker > failureLocal);
        assertTrue(infoCache > textureTracker);
        assertTrue(pbrManager > infoCache);
        assertTrue(logFailure > pbrManager);
        assertTrue(helperBody.contains("notification.run();"));
        assertTrue(helperBody.contains("catch (RuntimeException | Error exception)"));
        assertTrue(helperBody.contains("suppressNotificationFailure(failure, exception);"));
    }

    @Test
    public void textureDeleteNotificationSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("delete");

        suppressNotificationFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void textureDeleteNotificationSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("delete");
        RuntimeException cleanupFailure = new RuntimeException("cleanup");

        suppressNotificationFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void directGl11TextureUploadsNotifyLifecycleTracker() throws IOException {
        List<String> mismatches = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> requireTrackedDirectCalls(path, "GL11.glTexImage2D(",
                    "TextureLifecycleTracker.onTexImage2D(", "uploads", mismatches));
        }

        assertTrue(mismatches.toString(), mismatches.isEmpty());
    }

    @Test
    public void directGl11CopyTextureUploadsNotifyLifecycleTracker() throws IOException {
        List<String> mismatches = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> requireTrackedDirectCalls(path, "GL11.glCopyTexImage2D(",
                    "TextureLifecycleTracker.onCopyTexImage2D(", "copy uploads", mismatches));
        }

        assertTrue(mismatches.toString(), mismatches.isEmpty());
    }

    @Test
    public void directGl11TextureDeletesNotifyLifecycleTracker() throws IOException {
        List<String> mismatches = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> requireTrackedDirectCalls(path, "GL11.glDeleteTextures(",
                    "TextureLifecycleTracker.onDeleteTexture(", "deletes", mismatches));
        }

        assertTrue(mismatches.toString(), mismatches.isEmpty());
    }

    private static void suppressNotificationFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = TextureLifecycleTracker.class.getDeclaredMethod(
            "suppressNotificationFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static void requireTrackedDirectCalls(Path path,
                                                  String directCall,
                                                  String trackerCall,
                                                  String label,
                                                  List<String> mismatches) {
        String source = read(path);
        int searchIndex = 0;
        while (true) {
            int directIndex = source.indexOf(directCall, searchIndex);
            if (directIndex < 0) {
                return;
            }

            int statementEnd = source.indexOf(';', directIndex);
            int nextDirectIndex = source.indexOf(directCall, directIndex + directCall.length());
            int trackerIndex = source.indexOf(trackerCall, statementEnd < 0 ? directIndex : statementEnd);
            if (trackerIndex < 0 || (nextDirectIndex >= 0 && trackerIndex > nextDirectIndex)) {
                mismatches.add(path + " untracked " + label + " call near offset " + directIndex);
            }

            searchIndex = directIndex + directCall.length();
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read " + path, exception);
        }
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Could not parse method body for " + signature);
    }

}

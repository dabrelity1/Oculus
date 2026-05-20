package net.oculus.shaderpack.discovery;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;

/**
 * Lightweight manager for the shaderpack directory. Mirrors the Iris 1.16
 * implementation so the GUI and backend logic can be ported without churn.
 */
public final class ShaderpackDirectoryManager {
    private final Path root;

    public ShaderpackDirectoryManager(Path root) {
        this.root = root;
    }

    public static Collection<String> findShaderPacks() throws IOException {
        Path shaderpackRoot = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");

        if (!Files.exists(shaderpackRoot)) {
            Files.createDirectories(shaderpackRoot);
        }

        return new ShaderpackDirectoryManager(shaderpackRoot).enumerate();
    }

    public void copyPackIntoDirectory(String name, Path source) throws IOException {
        Path target = root.resolve(name);

        Files.copy(source, target);

        if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.walk(source)) {
                for (Path directory : stream.filter(Files::isDirectory).collect(Collectors.toList())) {
                    Path relative = source.relativize(directory);
                    Path targetDirectory = target.resolve(relative);

                    if (Files.exists(targetDirectory)) {
                        continue;
                    }

                    Files.createDirectory(targetDirectory);
                }
            }

            try (Stream<Path> stream = Files.walk(source)) {
                for (Path file : stream.filter(path -> !Files.isDirectory(path)).collect(Collectors.toSet())) {
                    Path relative = source.relativize(file);
                    Files.copy(file, target.resolve(relative));
                }
            }
        }
    }

    public Collection<String> enumerate() throws IOException {
        Comparator<String> baseComparator = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
        Comparator<String> comparator = (a, b) -> {
            String cleanedA = removeFormatting(a);
            String cleanedB = removeFormatting(b);
            return baseComparator.compare(cleanedA, cleanedB);
        };

        try (Stream<Path> list = Files.list(root)) {
            return list.filter(ShaderpackDirectoryManager::isValidToShowPack)
                .map(path -> path.getFileName().toString())
                .sorted(comparator)
                .collect(Collectors.toList());
        }
    }

    private static boolean isValidToShowPack(Path path) {
        return Files.isDirectory(path) || path.getFileName().toString().endsWith(".zip");
    }

    private static String removeFormatting(String formatted) {
        char[] original = formatted.toCharArray();
        char[] cleaned = new char[original.length];
        int count = 0;

        for (int i = 0; i < original.length; i++) {
            if (original[i] == '\u00a7') {
                i++;
            } else {
                cleaned[count++] = original[i];
            }
        }

        return new String(cleaned, 0, count);
    }

    public URI getDirectoryUri() {
        return root.toUri();
    }
}

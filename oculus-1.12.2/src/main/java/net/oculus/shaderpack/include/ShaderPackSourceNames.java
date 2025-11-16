package net.oculus.shaderpack.include;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import net.oculus.shaderpack.loading.ProgramArrayId;
import net.oculus.shaderpack.loading.ProgramId;

/**
 * Enumerates possible shader source file names present in a shader pack.
 */
public final class ShaderPackSourceNames {
    public static final ImmutableList<String> POTENTIAL_STARTS = findPotentialStarts();

    private ShaderPackSourceNames() {
    }

    public static boolean findPresentSources(ImmutableList.Builder<AbsolutePackPath> starts, Path packRoot,
                                              AbsolutePackPath directory, ImmutableList<String> candidates) throws IOException {
        Path directoryPath = directory.resolved(packRoot);

        if (!Files.exists(directoryPath)) {
            return false;
        }

        boolean anyFound = false;

        Set<String> found;
        try (Stream<Path> stream = Files.list(directoryPath)) {
            found = stream.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }

        for (String candidate : candidates) {
            if (found.contains(candidate)) {
                starts.add(directory.resolve(candidate));
                anyFound = true;
            }
        }

        return anyFound;
    }

    private static ImmutableList<String> findPotentialStarts() {
        ImmutableList.Builder<String> potentialFileNames = ImmutableList.builder();

        for (ProgramArrayId programArrayId : ProgramArrayId.values()) {
            for (int i = 0; i < programArrayId.getNumPrograms(); i++) {
                String name = programArrayId.getSourcePrefix();
                String suffix = i == 0 ? "" : Integer.toString(i);

                addComputeStarts(potentialFileNames, name + suffix);
            }
        }

        for (ProgramId programId : ProgramId.values()) {
            if (programId == ProgramId.Final || programId == ProgramId.Shadow) {
                addComputeStarts(potentialFileNames, programId.getSourceName());
            } else {
                addStarts(potentialFileNames, programId.getSourceName());
            }
        }

        return potentialFileNames.build();
    }

    private static void addStarts(ImmutableList.Builder<String> potentialFileNames, String baseName) {
        potentialFileNames.add(baseName + ".vsh");
        potentialFileNames.add(baseName + ".gsh");
        potentialFileNames.add(baseName + ".fsh");
    }

    private static void addComputeStarts(ImmutableList.Builder<String> potentialFileNames, String baseName) {
        addStarts(potentialFileNames, baseName);

        for (int j = 0; j < 27; j++) {
            String suffix = j == 0 ? "" : "_" + (char) ('a' + j - 1);
            potentialFileNames.add(baseName + suffix + ".csh");
        }
    }
}

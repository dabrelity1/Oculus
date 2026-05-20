package net.oculus.shaderpack.option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import net.oculus.Oculus;
import net.oculus.shaderpack.option.values.OptionValues;

public final class ProfileSet {
    private static final ProfileSet EMPTY = new ProfileSet(new LinkedHashMap<>());

    private final LinkedHashMap<String, Profile> orderedProfiles;
    private final List<Profile> sortedProfiles;

    private ProfileSet(LinkedHashMap<String, Profile> orderedProfiles) {
        this.orderedProfiles = orderedProfiles;
        List<Profile> sorted = new ArrayList<>(orderedProfiles.values());
        sorted.sort(Comparator.comparingInt((Profile profile) -> profile.precedence).reversed());
        this.sortedProfiles = Collections.unmodifiableList(sorted);
    }

    public static ProfileSet fromMap(Map<String, List<String>> definitions, OptionSet optionSet) {
        if (definitions == null || definitions.isEmpty() || optionSet == null) {
            return empty();
        }

        LinkedHashMap<String, Profile> ordered = new LinkedHashMap<>();
        definitions.keySet().forEach(name -> {
            ordered.put(name, parseProfile(name, new ArrayList<>(), definitions, optionSet));
        });

        if (ordered.isEmpty()) {
            return empty();
        }

        return new ProfileSet(ordered);
    }

    public static ProfileSet empty() {
        return EMPTY;
    }

    public int size() {
        return sortedProfiles.size();
    }

    public void forEach(BiConsumer<String, Profile> consumer) {
        orderedProfiles.forEach(consumer);
    }

    public Optional<Profile> get(String name) {
        return Optional.ofNullable(orderedProfiles.get(name));
    }

    public ProfileResult scan(OptionSet optionSet, OptionValues values) {
        if (sortedProfiles.isEmpty()) {
            return ProfileResult.empty();
        }

        for (int i = 0; i < sortedProfiles.size(); i++) {
            Profile profile = sortedProfiles.get(i);
            if (profile.matches(optionSet, values)) {
                Profile next = sortedProfiles.get(Math.floorMod(i + 1, sortedProfiles.size()));
                Profile previous = sortedProfiles.get(Math.floorMod(i - 1, sortedProfiles.size()));
                return ProfileResult.of(profile, next, previous);
            }
        }

        Profile next = sortedProfiles.get(0);
        Profile previous = sortedProfiles.get(sortedProfiles.size() - 1);
        return ProfileResult.of(null, next, previous);
    }

    private static Profile parseProfile(String name,
                                        List<String> parents,
                                        Map<String, List<String>> definitions,
                                        OptionSet optionSet) {
        List<String> tokens = definitions.get(name);
        if (tokens == null) {
            throw new IllegalArgumentException("Profile \"" + name + "\" does not exist!");
        }

        Profile.Builder builder = new Profile.Builder(name);

        for (String token : tokens) {
            if (token == null) {
                continue;
            }

            if (token.startsWith("!program.")) {
                builder.disableProgram(token.substring("!program.".length()));
                continue;
            }

            if (token.startsWith("profile.")) {
                String dependency = token.substring("profile.".length());
                if (parents.contains(dependency)) {
                    throw new IllegalArgumentException("Error parsing profile \"" + name
                        + "\", recursively included by: " + String.join(", ", parents));
                }
                parents.add(dependency);
                builder.addAll(parseProfile(dependency, parents, definitions, optionSet));
                continue;
            }

            if (token.startsWith("!")) {
                builder.option(token.substring(1), "false");
                continue;
            }

            int separatorIndex = firstSeparatorIndex(token);
            if (separatorIndex != -1) {
                builder.option(token.substring(0, separatorIndex), token.substring(separatorIndex + 1));
                continue;
            }

            if (optionSet.isBooleanOption(token)) {
                builder.option(token, "true");
            } else {
                Oculus.LOGGER.warn("Shader profile '{}' references unknown option '{}'", name, token);
            }
        }

        return builder.build();
    }

    private static int firstSeparatorIndex(String token) {
        int equals = token.indexOf('=');
        return equals == -1 ? token.indexOf(':') : equals;
    }

    public static final class ProfileResult {
        public final Optional<Profile> current;
        public final Optional<Profile> next;
        public final Optional<Profile> previous;

        private ProfileResult(Profile current, Profile next, Profile previous) {
            this.current = Optional.ofNullable(current);
            this.next = Optional.ofNullable(next);
            this.previous = Optional.ofNullable(previous);
        }

        private static ProfileResult empty() {
            return new ProfileResult(null, null, null);
        }

        private static ProfileResult of(Profile current, Profile next, Profile previous) {
            return new ProfileResult(current, next, previous);
        }
    }
}

package net.oculus.shaderpack.option;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
        Map<String, Profile> cache = new ConcurrentHashMap<>();

        definitions.keySet().forEach(name -> {
            Profile profile = parseProfile(name, new ArrayDeque<>(), definitions, optionSet, cache);
            if (profile != null) {
                ordered.put(name, profile);
            }
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
                                        Deque<String> lineage,
                                        Map<String, List<String>> definitions,
                                        OptionSet optionSet,
                                        Map<String, Profile> cache) {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        if (lineage.contains(name)) {
            warnRecursiveProfile(name, lineage);
            return null;
        }

        List<String> tokens = definitions.get(name);
        if (tokens == null) {
            Oculus.LOGGER.warn("Shader profile '{}' is referenced but not defined", name);
            return null;
        }

        lineage.push(name);
        Profile.Builder builder = new Profile.Builder(name);

        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }

            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("!program.")) {
                String program = trimmed.substring("!program.".length());
                if (!program.isEmpty()) {
                    builder.disableProgram(program);
                } else {
                    Oculus.LOGGER.warn("Shader profile '{}' has an empty program disable directive", name);
                }
                continue;
            }

            if (trimmed.startsWith("profile.")) {
                String dependency = trimmed.substring("profile.".length());
                if (dependency.isEmpty()) {
                    Oculus.LOGGER.warn("Shader profile '{}' referenced an empty profile", name);
                    continue;
                }

                Profile dependencyProfile = parseProfile(dependency, lineage, definitions, optionSet, cache);
                if (dependencyProfile != null) {
                    builder.addAll(dependencyProfile);
                }
                continue;
            }

            if (trimmed.startsWith("!")) {
                String optionName = trimmed.substring(1);
                if (optionSet.isBooleanOption(optionName)) {
                    builder.option(optionName, "false");
                } else {
                    Oculus.LOGGER.warn("Shader profile '{}' attempted to negate non-boolean option '{}'", name, optionName);
                }
                continue;
            }

            int separatorIndex = firstSeparatorIndex(trimmed);
            if (separatorIndex != -1) {
                String optionName = trimmed.substring(0, separatorIndex);
                String optionValue = trimmed.substring(separatorIndex + 1);
                if (!optionName.isEmpty() && !optionValue.isEmpty()) {
                    builder.option(optionName, optionValue);
                } else {
                    Oculus.LOGGER.warn("Shader profile '{}' has malformed assignment '{}'", name, trimmed);
                }
                continue;
            }

            if (optionSet.isBooleanOption(trimmed)) {
                builder.option(trimmed, "true");
            } else {
                Oculus.LOGGER.warn("Shader profile '{}' references unknown option '{}'", name, trimmed);
            }
        }

        lineage.pop();
        Profile profile = builder.build();
        cache.put(name, profile);
        return profile;
    }

    private static int firstSeparatorIndex(String token) {
        int equals = token.indexOf('=');
        int colon = token.indexOf(':');

        if (equals == -1) {
            return colon;
        }
        if (colon == -1) {
            return equals;
        }
        return Math.min(equals, colon);
    }

    private static void warnRecursiveProfile(String name, Deque<String> lineage) {
        List<String> cycle = new ArrayList<>(lineage);
        Collections.reverse(cycle);
        String chain = String.join(" -> ", cycle);
        Oculus.LOGGER.warn("Detected recursive shader profile definition involving '{}': {}", name, chain);
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

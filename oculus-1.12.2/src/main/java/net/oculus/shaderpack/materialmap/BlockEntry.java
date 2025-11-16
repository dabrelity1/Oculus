package net.oculus.shaderpack.materialmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.oculus.Oculus;

/**
 * Representation of a block entry in block.properties.
 * Supports optional namespace and property predicates like
 * {@code lantern:hanging=false:waterlogged=true}.
 */
public final class BlockEntry {
    private final NamespacedId id;
    private final Map<String, String> propertyPredicates;

    public BlockEntry(NamespacedId id, Map<String, String> propertyPredicates) {
        this.id = Objects.requireNonNull(id, "id");
        this.propertyPredicates = propertyPredicates == null ? Collections.emptyMap() : propertyPredicates;
    }

    public static BlockEntry parse(String entry) {
        if (entry == null || entry.isEmpty()) {
            throw new IllegalArgumentException("BlockEntry cannot parse empty strings");
        }

        String[] splitStates = entry.split(":");

        if (splitStates.length == 1) {
            return new BlockEntry(new NamespacedId("minecraft", entry), Collections.emptyMap());
        }

        if (splitStates.length == 2 && !splitStates[1].contains("=")) {
            return new BlockEntry(new NamespacedId(splitStates[0], splitStates[1]), Collections.emptyMap());
        }

        int statesStart;
        NamespacedId id;

        if (splitStates[1].contains("=")) {
            statesStart = 1;
            id = new NamespacedId("minecraft", splitStates[0]);
        } else {
            statesStart = 2;
            id = new NamespacedId(splitStates[0], splitStates[1]);
        }

        Map<String, String> map = new HashMap<>();
        for (int index = statesStart; index < splitStates.length; index++) {
            String[] propertyParts = splitStates[index].split("=");
            if (propertyParts.length != 2) {
                Oculus.LOGGER.warn("Failed to parse block state predicate '{}' in '{}'", splitStates[index], entry);
                continue;
            }
            map.put(propertyParts[0], propertyParts[1]);
        }

        return new BlockEntry(id, map);
    }

    public NamespacedId getId() {
        return id;
    }

    public Map<String, String> getPropertyPredicates() {
        return propertyPredicates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockEntry)) {
            return false;
        }
        BlockEntry that = (BlockEntry) o;
        return id.equals(that.id) && propertyPredicates.equals(that.propertyPredicates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, propertyPredicates);
    }
}

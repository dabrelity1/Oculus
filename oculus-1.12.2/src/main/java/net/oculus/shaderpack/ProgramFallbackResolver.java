package net.oculus.shaderpack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.oculus.shaderpack.loading.ProgramId;

public final class ProgramFallbackResolver {
    private final ProgramSet programs;
    private final Map<ProgramId, ProgramSource> cache;

    public ProgramFallbackResolver(ProgramSet programs) {
        this.programs = programs;
        this.cache = new HashMap<>();
    }

    public Optional<ProgramSource> resolve(ProgramId id) {
        return Optional.ofNullable(resolveNullable(id));
    }

    public ProgramSource resolveNullable(ProgramId id) {
        if (id == null) {
            return null;
        }

        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        ProgramSource source = programs.get(id).orElse(null);
        if (source == null) {
            ProgramId fallback = id.getFallback().orElse(null);
            if (fallback != null) {
                source = resolveNullable(fallback);
            }
        }

        cache.put(id, source);
        return source;
    }
}

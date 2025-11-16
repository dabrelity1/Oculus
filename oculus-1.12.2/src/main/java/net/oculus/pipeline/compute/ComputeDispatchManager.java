package net.oculus.pipeline.compute;

import java.util.Objects;

import net.oculus.gl.program.ComputeProgram;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.SamplerOverrideProvider;
import net.oculus.shaderpack.ComputeSource;
import net.oculus.shaderpack.ProgramSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compiles and dispatches compute shader programs that belong to the current shader pack.
 * The 1.12.2 port only needs a thin orchestration layer, but mirroring the upstream
 * structure keeps the later rendering pipeline work straightforward.
 */
public final class ComputeDispatchManager {
    private static final Logger LOGGER = LogManager.getLogger(ComputeDispatchManager.class);

    private final SamplerOverrideProvider samplerOverrideProvider;
    private final ComputeProgram[] shadowComputes;
    private final ComputeProgram[][] shadowCompositeComputes;
    private final ComputeProgram[][] prepareComputes;
    private final ComputeProgram[][] deferredComputes;
    private final ComputeProgram[][] compositeComputes;
    private final ComputeProgram[] finalComputes;

    public ComputeDispatchManager(ProgramSet programSet) {
        this(programSet, SamplerOverrideProvider.NONE);
    }

    public ComputeDispatchManager(ProgramSet programSet, SamplerOverrideProvider samplerOverrideProvider) {
        Objects.requireNonNull(programSet, "programSet");
        this.samplerOverrideProvider = samplerOverrideProvider == null ? SamplerOverrideProvider.NONE : samplerOverrideProvider;

        this.shadowComputes = compileArray(programSet.getShadowCompute());
        this.shadowCompositeComputes = compileMatrix(programSet.getShadowCompCompute());
        this.prepareComputes = compileMatrix(programSet.getPrepareCompute());
        this.deferredComputes = compileMatrix(programSet.getDeferredCompute());
        this.compositeComputes = compileMatrix(programSet.getCompositeCompute());
        this.finalComputes = compileArray(programSet.getFinalCompute());
    }

    public boolean hasComputes() {
        return hasEntries(shadowComputes)
            || hasEntries(finalComputes)
            || hasEntries(shadowCompositeComputes)
            || hasEntries(prepareComputes)
            || hasEntries(deferredComputes)
            || hasEntries(compositeComputes);
    }

    public void dispatchFrame(int primaryWidth, int primaryHeight, int shadowResolution) {
        if (!hasComputes()) {
            return;
        }

        if (shadowResolution > 0) {
            dispatchArray("shadow", shadowComputes, shadowResolution, shadowResolution);
            dispatchMatrix("shadowcomp", shadowCompositeComputes, shadowResolution, shadowResolution);
        }

        if (primaryWidth > 0 && primaryHeight > 0) {
            dispatchMatrix("prepare", prepareComputes, primaryWidth, primaryHeight);
            dispatchMatrix("deferred", deferredComputes, primaryWidth, primaryHeight);
            dispatchMatrix("composite", compositeComputes, primaryWidth, primaryHeight);
            dispatchArray("final", finalComputes, primaryWidth, primaryHeight);
        }
    }

    public void destroy() {
        destroy(shadowComputes);
        destroy(shadowCompositeComputes);
        destroy(prepareComputes);
        destroy(deferredComputes);
        destroy(compositeComputes);
        destroy(finalComputes);
    }

    private void dispatchMatrix(String stage, ComputeProgram[][] programs, float width, float height) {
        if (programs == null) {
            return;
        }
        for (ComputeProgram[] row : programs) {
            dispatchArray(stage, row, width, height);
        }
    }

    private void dispatchArray(String stage, ComputeProgram[] programs, float width, float height) {
        if (programs == null || width <= 0 || height <= 0) {
            return;
        }

        for (ComputeProgram program : programs) {
            if (program == null) {
                continue;
            }

            try {
                program.dispatch(width, height);
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to dispatch compute program {} during stage {}", program, stage, ex);
            }
        }
    }

    private ComputeProgram[] compileArray(ComputeSource[] sources) {
        if (sources == null || sources.length == 0) {
            return null;
        }

        ComputeProgram[] compiled = new ComputeProgram[sources.length];
        for (int i = 0; i < sources.length; i++) {
            compiled[i] = compileCompute(sources[i]);
        }
        return compiled;
    }

    private ComputeProgram[][] compileMatrix(ComputeSource[][] sources) {
        if (sources == null || sources.length == 0) {
            return null;
        }

        ComputeProgram[][] compiled = new ComputeProgram[sources.length][];
        for (int i = 0; i < sources.length; i++) {
            compiled[i] = compileArray(sources[i]);
        }
        return compiled;
    }

    private ComputeProgram compileCompute(ComputeSource source) {
        if (source == null || !source.isValid()) {
            return null;
        }

        String computeSource = source.getSource().orElse(null);
        if (computeSource == null) {
            return null;
        }

        try {
            SamplerOverrideMap overrides = samplerOverrideProvider.overridesFor(source.getName());
            ProgramBuilder builder = ProgramBuilder.beginCompute(source.getName(), computeSource, overrides);
            ComputeProgram program = builder.buildCompute();
            program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
            LOGGER.debug("Compiled compute shader {}", source.getName());
            return program;
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to compile compute shader {}", source.getName(), ex);
            return null;
        }
    }

    private boolean hasEntries(ComputeProgram[] programs) {
        if (programs == null) {
            return false;
        }
        for (ComputeProgram program : programs) {
            if (program != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEntries(ComputeProgram[][] programs) {
        if (programs == null) {
            return false;
        }
        for (ComputeProgram[] row : programs) {
            if (hasEntries(row)) {
                return true;
            }
        }
        return false;
    }

    private void destroy(ComputeProgram[] programs) {
        if (programs == null) {
            return;
        }
        for (ComputeProgram program : programs) {
            if (program != null) {
                try {
                    program.destroy();
                } catch (RuntimeException ex) {
                    LOGGER.warn("Exception while destroying compute program", ex);
                }
            }
        }
    }

    private void destroy(ComputeProgram[][] programs) {
        if (programs == null) {
            return;
        }
        for (ComputeProgram[] row : programs) {
            destroy(row);
        }
    }
}

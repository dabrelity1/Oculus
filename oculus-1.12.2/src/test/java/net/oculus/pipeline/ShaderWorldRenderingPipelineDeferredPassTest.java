package net.oculus.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.function.Function;

import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.include.AbsolutePackPath;
import org.junit.Test;

public class ShaderWorldRenderingPipelineDeferredPassTest {
    @Test
    public void missingDeferredProgramPlaceholdersDoNotCountAsDeferredPasses() {
        ProgramSet set = programSet(path -> null);

        assertFalse(ShaderWorldRenderingPipeline.hasDeferredPasses(set));
    }

    @Test
    public void validDeferredRasterSourceCountsAsDeferredPass() {
        ProgramSet set = programSet(path -> {
            String key = path.getPathString();
            if ("/deferred1.vsh".equals(key) || "/deferred1.fsh".equals(key)) {
                return "#version 120\nvoid main() {}\n";
            }
            return null;
        });

        assertTrue(ShaderWorldRenderingPipeline.hasDeferredPasses(set));
    }

    @Test
    public void validDeferredComputeSourceCountsAsDeferredPass() {
        ProgramSet set = programSet(path -> {
            if ("/deferred1.csh".equals(path.getPathString())) {
                return "#version 430\nvoid main() {}\n";
            }
            return null;
        });

        assertTrue(ShaderWorldRenderingPipeline.hasDeferredPasses(set));
    }

    private static ProgramSet programSet(Function<AbsolutePackPath, String> sourceProvider) {
        return new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            sourceProvider,
            ShaderProperties.empty(),
            ShaderPack.of(
                "test",
                null,
                ShaderProperties.empty(),
                null,
                AbsolutePackPath.fromAbsolutePath("/"),
                path -> null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet()
            )
        );
    }
}

package net.oculus.pipeline;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.include.AbsolutePackPath;
import org.junit.Test;

public class ShaderWorldRenderingPipelineUnsupportedFeatureTest {
    @Test
    public void enabledShadowCompositeRasterPassFailsClearly() {
        ProgramSet programSet = programSet(path -> {
            String key = path.getPathString();
            if ("/shadowcomp.vsh".equals(key) || "/shadowcomp.fsh".equals(key)) {
                return "#version 120\nvoid main() {}\n";
            }
            return null;
        });

        assertUnsupportedShadowComposite(programSet, "shadowcomp");
    }

    @Test
    public void enabledShadowCompositeComputePassIsAccepted() {
        ProgramSet programSet = programSet(path -> {
            if ("/shadowcomp.csh".equals(path.getPathString())) {
                return "#version 430\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}\n";
            }
            return null;
        });

        ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet);
    }

    @Test
    public void missingShadowCompositePassesAreAccepted() {
        ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet(path -> null));
    }

    @Test
    public void incompleteShadowCompositeRasterSourceIsNotTreatedAsExecutablePass() {
        ProgramSet programSet = programSet(path -> {
            if ("/shadowcomp.fsh".equals(path.getPathString())) {
                return "#version 120\nvoid main() {}\n";
            }
            return null;
        });

        ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet);
    }

    @Test
    public void disabledDimensionShadowCompositeComputePassIsAccepted() {
        ProgramSet programSet = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/world0"),
            path -> {
                if ("/world0/shadowcomp.csh".equals(path.getPathString())) {
                    return "#version 430\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}\n";
                }
                return null;
            },
            ShaderProperties.empty(),
            packWithDisabledPrograms("world0/shadowcomp")
        );

        ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet);
    }

    @Test
    public void disabledDimensionShadowCompositeRasterPassIsAccepted() {
        ProgramSet programSet = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/world1"),
            path -> {
                String key = path.getPathString();
                if ("/world1/shadowcomp.vsh".equals(key) || "/world1/shadowcomp.fsh".equals(key)) {
                    return "#version 120\nvoid main() {}\n";
                }
                return null;
            },
            ShaderProperties.empty(),
            packWithDisabledPrograms("world1/shadowcomp")
        );

        ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet);
    }

    @Test
    public void complementaryDimensionShadowCompositeComputesAreAcceptedWhenDisabled() {
        assertDisabledDimensionShadowCompositeComputePassIsAccepted("/world-1", "world-1/shadowcomp");
        assertDisabledDimensionShadowCompositeComputePassIsAccepted("/world0", "world0/shadowcomp");
        assertDisabledDimensionShadowCompositeComputePassIsAccepted("/world1", "world1/shadowcomp");
    }

    private static void assertUnsupportedShadowComposite(ProgramSet programSet, String expectedName) {
        try {
            ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet);
            fail("Expected unsupported shadow composite pass to fail clearly");
        } catch (ProgramLoadException exception) {
            assertTrue(exception.getMessage().contains("unsupported raster shadow composite pass"));
            assertTrue(exception.getMessage().contains(expectedName));
            assertTrue(exception.getMessage().contains("compute-only shadowcomp"));
        }
    }

    private static ProgramSet programSet(Function<AbsolutePackPath, String> sourceProvider) {
        return new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            sourceProvider,
            ShaderProperties.empty(),
            null
        );
    }

    private static void assertDisabledDimensionShadowCompositeComputePassIsAccepted(String root, String disabledProgram) {
        ProgramSet programSet = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath(root),
            path -> {
                if ((root + "/shadowcomp.csh").equals(path.getPathString())) {
                    return "#version 430\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}\n";
                }
                return null;
            },
            ShaderProperties.empty(),
            packWithDisabledPrograms(disabledProgram)
        );

        ShaderWorldRenderingPipeline.rejectUnsupportedShadowCompositeRasterPrograms(programSet);
    }

    private static ShaderPack packWithDisabledPrograms(String... disabledPrograms) {
        Set<String> disabled = new HashSet<>(Arrays.asList(disabledPrograms));
        return ShaderPack.of(
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
            disabled
        );
    }
}

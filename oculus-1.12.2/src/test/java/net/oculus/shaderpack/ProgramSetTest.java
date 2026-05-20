package net.oculus.shaderpack;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import net.oculus.shaderpack.include.AbsolutePackPath;
import org.junit.Test;

public class ProgramSetTest {
    @Test
    public void rootDisabledProgramNameDoesNotSuppressWorldSpecificProgramSources() {
        ProgramSet set = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/world0"),
            world0ShadowCompProvider(),
            ShaderProperties.empty(),
            packWithDisabledPrograms("shadowcomp")
        );

        assertTrue(set.getShadowComposite()[0].requireValid().isPresent());
        assertNotNull(set.getShadowCompCompute()[0][0]);
    }

    @Test
    public void worldSpecificDisabledProgramNameSuppressesWorldSpecificProgramSources() {
        ProgramSet set = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/world0"),
            world0ShadowCompProvider(),
            ShaderProperties.empty(),
            packWithDisabledPrograms("world0/shadowcomp")
        );

        assertFalse(set.getShadowComposite()[0].requireValid().isPresent());
        assertNull(set.getShadowCompCompute()[0][0]);
    }

    @Test
    public void worldSpecificDisabledComputePassSuppressesAlphabeticVariants() {
        ProgramSet set = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/world0"),
            world0ShadowCompProvider(),
            ShaderProperties.empty(),
            packWithDisabledPrograms("world0/shadowcomp")
        );

        assertNull(set.getShadowCompCompute()[0][0]);
        assertNull(set.getShadowCompCompute()[0][1]);
    }

    @Test
    public void enabledComputePassKeepsAlphabeticVariants() {
        ProgramSet set = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/world0"),
            world0ShadowCompProvider(),
            ShaderProperties.empty(),
            packWithDisabledPrograms()
        );

        assertNotNull(set.getShadowCompCompute()[0][0]);
        assertNotNull(set.getShadowCompCompute()[0][1]);
    }

    @Test
    public void rootProgramSetStillUsesRootDisabledProgramName() {
        ProgramSet set = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            rootShadowCompProvider(),
            ShaderProperties.empty(),
            packWithDisabledPrograms("shadowcomp")
        );

        assertFalse(set.getShadowComposite()[0].requireValid().isPresent());
        assertNull(set.getShadowCompCompute()[0][0]);
    }

    @Test
    public void computeDirectiveCollectionKeepsReferenceFinalBeforeShadowOrder() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/shaderpack/ProgramSet.java")), StandardCharsets.UTF_8);

        int shadowComp = source.indexOf("ComputeSourceCollector.collect(computes, shadowCompCompute);");
        int finalCompute = source.indexOf("ComputeSourceCollector.collect(computes, finalCompute);");
        int shadowCompute = source.indexOf("ComputeSourceCollector.collect(computes, shadowCompute);");

        assertTrue(shadowComp >= 0);
        assertTrue(finalCompute > shadowComp);
        assertTrue(shadowCompute > finalCompute);
    }

    @Test
    public void missingDamagedBlockFallbackOverridesDrawBuffersAfterDirectiveCollection() {
        ProgramSet set = new ProgramSet(
            AbsolutePackPath.fromAbsolutePath("/"),
            damagedBlockFallbackProvider(),
            ShaderProperties.empty(),
            packWithDisabledPrograms()
        );

        ProgramSource damaged = set.getGbuffersDamagedBlock().get();
        assertTrue(damaged.getVertexSource().isPresent());
        assertArrayEquals(new int[] {0}, damaged.getDirectives().getDrawBuffers());
    }

    private static Function<AbsolutePackPath, String> world0ShadowCompProvider() {
        return path -> {
            String key = path.getPathString();
            if ("/world0/shadowcomp.vsh".equals(key) || "/world0/shadowcomp.fsh".equals(key)) {
                return "#version 120\nvoid main() {}\n";
            }
            if ("/world0/shadowcomp.csh".equals(key)) {
                return "#version 430\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}\n";
            }
            if ("/world0/shadowcomp_a.csh".equals(key)) {
                return "#version 430\nconst ivec3 workGroups = ivec3(2, 1, 1);\nvoid main() {}\n";
            }
            return null;
        };
    }

    private static Function<AbsolutePackPath, String> rootShadowCompProvider() {
        return path -> {
            String key = path.getPathString();
            if ("/shadowcomp.vsh".equals(key) || "/shadowcomp.fsh".equals(key)) {
                return "#version 120\nvoid main() {}\n";
            }
            if ("/shadowcomp.csh".equals(key)) {
                return "#version 430\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}\n";
            }
            return null;
        };
    }

    private static Function<AbsolutePackPath, String> damagedBlockFallbackProvider() {
        return path -> {
            String key = path.getPathString();
            if ("/gbuffers_terrain.vsh".equals(key)) {
                return "#version 120\nvoid main() {}\n";
            }
            if ("/gbuffers_terrain.fsh".equals(key)) {
                return "#version 120\n/* DRAWBUFFERS:12 */\nvoid main() {}\n";
            }
            return null;
        };
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

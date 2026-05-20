package net.oculus.uniforms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderHell;
import org.junit.Test;

public class WorldInfoUniformsTest {
    @Test
    public void worldInfoUniformsResolveMinecraftAtUseTime() throws Exception {
        String source = read("src/main/java/net/oculus/uniforms/WorldInfoUniforms.java");

        assertFalse(source.contains("private static final Minecraft"));
        assertTrue(source.contains("private static Minecraft getMinecraft()"));
        assertTrue(source.contains("Minecraft minecraft = getMinecraft();\n"
            + "        return minecraft != null ? minecraft.world : null;"));
    }

    @Test
    public void bedrockLevelMatchesOculus116Constant() {
        assertEquals(0, WorldInfoUniforms.getBedrockLevel());
    }

    @Test
    public void ceilingUsesNetherProviderFlagInsteadOfMissingSkylight() {
        assertEquals(1, WorldInfoUniforms.hasCeiling(new TestProvider(true, false, DimensionType.NETHER, 0.0f, 128.0f)));
        assertEquals(0, WorldInfoUniforms.hasCeiling(new TestProvider(false, false, DimensionType.OVERWORLD, 0.0f, 128.0f)));
        assertEquals(0, WorldInfoUniforms.hasCeiling(new TestProvider(false, true, DimensionType.OVERWORLD, 0.0f, 128.0f)));
        assertEquals(0, WorldInfoUniforms.hasCeiling(null));
    }

    @Test
    public void cloudHeightMatches116VanillaEffects() {
        assertEquals(192.0f, WorldInfoUniforms.getCloudHeight(null), 0.0f);
        assertEquals(128.0f, WorldInfoUniforms.getCloudHeight(new TestProvider(false, true, DimensionType.OVERWORLD, 0.0f, 128.0f)), 0.0f);
        assertTrue(Float.isNaN(WorldInfoUniforms.getCloudHeight(new WorldProviderHell())));
        assertTrue(Float.isNaN(WorldInfoUniforms.getCloudHeight(new TestProvider(false, false, DimensionType.THE_END, 0.0f, 8.0f))));
    }

    @Test
    public void logicalHeightLimitMatches116VanillaNetherDefault() {
        assertEquals(128, WorldInfoUniforms.getLogicalHeightLimit(new TestProvider(true, false, DimensionType.NETHER, 0.0f, 128.0f), 256));
        assertEquals(256, WorldInfoUniforms.getLogicalHeightLimit(new TestProvider(false, true, DimensionType.OVERWORLD, 0.0f, 128.0f), 256));
        assertEquals(256, WorldInfoUniforms.getLogicalHeightLimit(new TestProvider(false, false, DimensionType.THE_END, 0.0f, 8.0f), 256));
        assertEquals(384, WorldInfoUniforms.getLogicalHeightLimit(new TestProvider(false, true, DimensionType.OVERWORLD, 0.0f, 128.0f), 384));
    }

    @Test
    public void ambientLightMatches116VanillaNetherDefault() {
        assertEquals(0.1f, WorldInfoUniforms.getAmbientLight(new TestProvider(true, false, DimensionType.NETHER, 0.0f, 128.0f)), 0.0001f);
        assertEquals(0.0f, WorldInfoUniforms.getAmbientLight(new TestProvider(false, true, DimensionType.OVERWORLD, 0.0f, 128.0f)), 0.0001f);
        assertEquals(0.0f, WorldInfoUniforms.getAmbientLight(new TestProvider(false, false, DimensionType.THE_END, 0.0f, 8.0f)), 0.0001f);
        assertEquals(0.2f, WorldInfoUniforms.getAmbientLight(new TestProvider(false, true, DimensionType.OVERWORLD, 0.2f, 128.0f)), 0.0001f);
        assertEquals(0.0f, WorldInfoUniforms.getAmbientLight(null), 0.0001f);
    }

    @Test
    public void unreadableDimensionTypeFallsBackToProviderValues() {
        WorldProvider provider = new UnreadableDimensionTypeProvider(0.3f, 96.0f);

        assertEquals(96.0f, WorldInfoUniforms.getCloudHeight(provider), 0.0001f);
        assertEquals(384, WorldInfoUniforms.getLogicalHeightLimit(provider, 384));
        assertEquals(0.3f, WorldInfoUniforms.getAmbientLight(provider), 0.0001f);
    }

    private static final class TestProvider extends WorldProvider {
        private final boolean nether;
        private final boolean skylight;
        private final DimensionType dimensionType;
        private final float cloudHeight;

        private TestProvider(boolean nether, boolean skylight, DimensionType dimensionType,
                             float ambientLight, float cloudHeight) {
            this.nether = nether;
            this.skylight = skylight;
            this.dimensionType = dimensionType;
            this.cloudHeight = cloudHeight;
            this.lightBrightnessTable[0] = ambientLight;
        }

        @Override
        public boolean hasSkyLight() {
            return skylight;
        }

        @Override
        public boolean isNether() {
            return nether;
        }

        @Override
        public DimensionType getDimensionType() {
            return dimensionType;
        }

        @Override
        public float getCloudHeight() {
            return cloudHeight;
        }
    }

    private static final class UnreadableDimensionTypeProvider extends WorldProvider {
        private final float cloudHeight;

        private UnreadableDimensionTypeProvider(float ambientLight, float cloudHeight) {
            this.cloudHeight = cloudHeight;
            this.lightBrightnessTable[0] = ambientLight;
        }

        @Override
        public DimensionType getDimensionType() {
            throw new IllegalStateException("dimension type unavailable");
        }

        @Override
        public float getCloudHeight() {
            return cloudHeight;
        }
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

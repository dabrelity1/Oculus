package net.oculus.pipeline.sampler;

import static org.junit.Assert.assertEquals;

import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import org.junit.Test;

public class SamplerOverrideConfiguratorTest {
    @Test
    public void shadowOverridesOnlyExposeSupportedBackportTargets() {
        SamplerOverrideMap overrides = SamplerOverrideConfigurator
            .create(new PackDirectives(ShaderProperties.empty()))
            .buildProvider()
            .overridesFor("shadow");

        assertEquals(5, overrides.resolve("oculus_shadow_depth"));
        assertEquals(6, overrides.resolve("oculus_shadow_color"));
        assertEquals(7, overrides.resolve("oculus_shadow_color1"));
        assertEquals(-1, overrides.resolve("oculus_shadow_color2"));
    }
}

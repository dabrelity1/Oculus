package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.oculus.vendored.joml.Vector4f;

public class ShadowColorClearDirectiveTest {
    @Test
    public void parsesShadowColorClearDirectives() {
        PackDirectives directives = new PackDirectives(ShaderProperties.empty());
        DispatchingDirectiveHolder holder = new DispatchingDirectiveHolder();
        directives.acceptDirectivesFrom(holder);

        for (ConstDirectiveParser.ConstDirective directive : ConstDirectiveParser.findDirectives(
            "const bool shadowcolor0Clear = false;\n"
                + "const bool shadowcolor1Clear = true;\n"
                + "const vec4 shadowcolor1ClearColor = vec4(0.25, 0.5, 0.75, 1.0);\n")) {
            holder.processDirective(directive);
        }

        PackShadowDirectives shadowDirectives = directives.getShadowDirectives();
        assertFalse(shadowDirectives.getColorSamplingSettings().get(0).shouldClear());
        assertTrue(shadowDirectives.getColorSamplingSettings().get(1).shouldClear());

        Vector4f clearColor = shadowDirectives.getColorSamplingSettings().get(1).getClearColor();
        assertEquals(0.25F, clearColor.x(), 0.0F);
        assertEquals(0.5F, clearColor.y(), 0.0F);
        assertEquals(0.75F, clearColor.z(), 0.0F);
        assertEquals(1.0F, clearColor.w(), 0.0F);
    }
}

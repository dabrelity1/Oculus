package net.oculus.shaderpack;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShadowRenderDirectiveTest {
    @Test
    public void defaultsMatchReferenceShadowRenderToggles() {
        PackShadowDirectives shadowDirectives =
            new PackDirectives(ShaderProperties.empty()).getShadowDirectives();

        assertTrue(shadowDirectives.shouldRenderTerrain());
        assertTrue(shadowDirectives.shouldRenderTranslucent());
        assertTrue(shadowDirectives.shouldRenderEntities());
        assertFalse(shadowDirectives.shouldRenderPlayer());
        assertTrue(shadowDirectives.shouldRenderBlockEntities());
    }

    @Test
    public void parsesShadowRenderToggles() {
        PackShadowDirectives shadowDirectives = new PackDirectives(new ShaderProperties(
            "shadowTerrain=false\n"
                + "shadowTranslucent=false\n"
                + "shadowEntities=false\n"
                + "shadowPlayer=true\n"
                + "shadowBlockEntities=false\n")).getShadowDirectives();

        assertFalse(shadowDirectives.shouldRenderTerrain());
        assertFalse(shadowDirectives.shouldRenderTranslucent());
        assertFalse(shadowDirectives.shouldRenderEntities());
        assertTrue(shadowDirectives.shouldRenderPlayer());
        assertFalse(shadowDirectives.shouldRenderBlockEntities());
    }
}

package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EntityShadowDistanceDirectiveTest {
    @Test
    public void parsesEntityShadowDistanceMultiplierDirective() {
        PackDirectives directives = new PackDirectives(ShaderProperties.empty());
        DispatchingDirectiveHolder holder = new DispatchingDirectiveHolder();
        directives.acceptDirectivesFrom(holder);

        for (ConstDirectiveParser.ConstDirective directive : ConstDirectiveParser.findDirectives(
            "const float entityShadowDistanceMul = 0.125;\n")) {
            holder.processDirective(directive);
        }

        assertEquals(0.125F, directives.getShadowDirectives().getEntityShadowDistanceMul(), 0.0F);
    }
}

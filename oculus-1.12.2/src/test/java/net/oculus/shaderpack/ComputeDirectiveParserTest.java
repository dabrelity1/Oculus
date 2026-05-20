package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Optional;

import org.junit.Test;

import net.oculus.vendored.joml.Vector2f;
import net.oculus.vendored.joml.Vector3i;

public class ComputeDirectiveParserTest {
    @Test
    public void workGroupsWithExtraArgumentsUsesLeadingArgumentsLikeReference() {
        ComputeSource source = new ComputeSource("composite", "#version 430\nvoid main() {}\n", null);

        ComputeDirectiveParser.setComputeWorkGroups(source,
            parse("const ivec3 workGroups = ivec3(16, 8, 4, 99);\n"));

        Vector3i value = source.getWorkGroups();
        assertEquals(16, value.x());
        assertEquals(8, value.y());
        assertEquals(4, value.z());
    }

    @Test
    public void workGroupsRenderWithExtraArgumentsUsesLeadingArgumentsLikeReference() {
        ComputeSource source = new ComputeSource("composite", "#version 430\nvoid main() {}\n", null);

        ComputeDirectiveParser.setComputeWorkGroupsRelative(source,
            parse("const vec2 workGroupsRender = vec2(0.5, 0.25, 99.0);\n"));

        Vector2f value = source.getWorkGroupRelative();
        assertEquals(0.5F, value.x(), 0.0F);
        assertEquals(0.25F, value.y(), 0.0F);
    }

    @Test
    public void invalidWorkGroupNumbersAreLoggedAndIgnoredLikeReference() {
        ComputeSource source = new ComputeSource("composite", "#version 430\nvoid main() {}\n", null);

        ComputeDirectiveParser.setComputeWorkGroups(source,
            parse("const ivec3 workGroups = ivec3(16, nope, 4);\n"));

        assertNull(source.getWorkGroups());
    }

    private static ConstDirectiveParser.ConstDirective parse(String line) {
        Optional<ConstDirectiveParser.ConstDirective> directive = ConstDirectiveParser.findDirectiveInLine(line);
        if (!directive.isPresent()) {
            throw new AssertionError("Expected const directive: " + line);
        }
        return directive.get();
    }
}

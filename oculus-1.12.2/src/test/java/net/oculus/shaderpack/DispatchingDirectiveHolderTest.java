package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import net.oculus.vendored.joml.Vector3i;
import net.oculus.vendored.joml.Vector4f;

public class DispatchingDirectiveHolderTest {
    @Test
    public void vectorDirectivesWithExtraArgumentsUseLeadingArgumentsLikeReference() {
        DispatchingDirectiveHolder holder = new DispatchingDirectiveHolder();
        AtomicReference<Vector4f> value = new AtomicReference<>();
        holder.acceptConstVec4Directive("colortex0ClearColor", value::set);

        holder.processDirective(parse("const vec4 colortex0ClearColor = vec4(0.25, 0.5, 0.75, 1.0, 99.0);\n"));

        assertEquals(0.25F, value.get().x(), 0.0F);
        assertEquals(0.5F, value.get().y(), 0.0F);
        assertEquals(0.75F, value.get().z(), 0.0F);
        assertEquals(1.0F, value.get().w(), 0.0F);
    }

    @Test
    public void ivec3DirectivesWithExtraArgumentsUseLeadingArgumentsLikeReference() {
        DispatchingDirectiveHolder holder = new DispatchingDirectiveHolder();
        AtomicReference<Vector3i> value = new AtomicReference<>();
        holder.acceptConstIVec3Directive("workGroups", value::set);

        holder.processDirective(parse("const ivec3 workGroups = ivec3(16, 8, 4, 99);\n"));

        assertEquals(16, value.get().x());
        assertEquals(8, value.get().y());
        assertEquals(4, value.get().z());
    }

    @Test
    public void vectorDirectivesWithInvalidNumbersAreLoggedAndIgnoredLikeReference() {
        DispatchingDirectiveHolder holder = new DispatchingDirectiveHolder();
        AtomicReference<Vector4f> value = new AtomicReference<>();
        holder.acceptConstVec4Directive("colortex0ClearColor", value::set);

        holder.processDirective(parse("const vec4 colortex0ClearColor = vec4(0.25, nope, 0.75, 1.0);\n"));

        assertNull(value.get());
    }

    private static ConstDirectiveParser.ConstDirective parse(String line) {
        Optional<ConstDirectiveParser.ConstDirective> directive = ConstDirectiveParser.findDirectiveInLine(line);
        if (!directive.isPresent()) {
            throw new AssertionError("Expected const directive: " + line);
        }
        return directive.get();
    }
}

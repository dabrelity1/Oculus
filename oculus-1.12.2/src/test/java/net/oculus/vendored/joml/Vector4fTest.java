package net.oculus.vendored.joml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class Vector4fTest {
    @Test
    public void valueEqualityMatchesReferenceJomlBehaviorForClearPassGrouping() {
        Vector4f first = new Vector4f(1.0F, 0.5F, 0.25F, 1.0F);
        Vector4f second = new Vector4f(1.0F, 0.5F, 0.25F, 1.0F);
        Vector4f different = new Vector4f(1.0F, 0.5F, 0.25F, 0.0F);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(new Vector4f(0.0F, 0.0F, 0.0F, 0.0F),
            new Vector4f(-0.0F, 0.0F, 0.0F, 0.0F));

        Map<Vector4f, Integer> groupedByClearColor = new HashMap<>();
        groupedByClearColor.put(first, 1);
        groupedByClearColor.put(second, groupedByClearColor.get(first) + 1);

        assertEquals(Integer.valueOf(2), groupedByClearColor.get(first));
        assertEquals(1, groupedByClearColor.size());
    }
}

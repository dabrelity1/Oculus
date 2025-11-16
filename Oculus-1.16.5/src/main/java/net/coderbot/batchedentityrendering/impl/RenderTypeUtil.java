package net.coderbot.batchedentityrendering.impl;

import org.lwjgl.opengl.GL11C;

import net.minecraft.client.renderer.RenderType;

public class RenderTypeUtil {
    public static boolean isTriangleStripDrawMode(RenderType renderType) {
        return renderType.mode() == GL11C.GL_TRIANGLE_STRIP;
    }
}

package net.oculus.pipeline;

import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;

public class WorldRenderContext {
    public WorldRenderContext(ICamera camera, float partialTicks, Matrix4f projectionMatrix, Entity renderViewEntity) {
        // Stub
    }

    public ICamera getCamera() {
        return null;
    }

    public float getPartialTicks() {
        return 0.0f;
    }

    public Matrix4f getProjectionMatrix() {
        return null;
    }

    public Entity getRenderViewEntity() {
        return null;
    }
}

package net.coderbot.iris.gl.framebuffer;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.EXTFramebufferObject;

/**
 * Minimal framebuffer wrapper for the LWJGL 2 backport.
 *
 * Minecraft 1.12.2 only exposes EXT_framebuffer_object, so we bind the FBO before each
 * attachment call instead of relying on Direct State Access.
 */
public final class GlFramebuffer extends GlResource {
    private final Int2IntMap colorAttachments = new Int2IntOpenHashMap();
    private boolean hasDepthAttachment;

    public GlFramebuffer() {
        super(IrisRenderSystem.createFramebuffer());
    }

    public void bind() {
        IrisRenderSystem.bindFramebuffer(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, getGlId());
    }

    public void bindForDrawing() {
        IrisRenderSystem.bindFramebuffer(EXTFramebufferObject.GL_DRAW_FRAMEBUFFER_EXT, getGlId());
    }

    public void bindForReading() {
        IrisRenderSystem.bindFramebuffer(EXTFramebufferObject.GL_READ_FRAMEBUFFER_EXT, getGlId());
    }

    public void attachColorTexture(int index, int textureTarget, int textureId) {
        bind();
        IrisRenderSystem.framebufferTexture2D(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT + index,
                textureTarget,
                textureId,
                0
        );
        colorAttachments.put(index, textureId);
    }

    public void attachDepthTexture(int textureTarget, int textureId, boolean containsStencil) {
        bind();
        int attachment = containsStencil
                ? EXTFramebufferObject.GL_DEPTH_STENCIL_ATTACHMENT_EXT
                : EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT;
        IrisRenderSystem.framebufferTexture2D(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                attachment,
                textureTarget,
                textureId,
                0
        );
        hasDepthAttachment = true;
    }

    public boolean hasDepthAttachment() {
        return hasDepthAttachment;
    }

    public int getColorAttachment(int index) {
        return colorAttachments.getOrDefault(index, 0);
    }

    public boolean checkComplete() {
        bind();
        int status = IrisRenderSystem.checkFramebufferStatus(EXTFramebufferObject.GL_FRAMEBUFFER_EXT);
        return status == EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT;
    }

    @Override
    protected void destroyInternal() {
        IrisRenderSystem.deleteFramebuffer(getGlId());
    }
}

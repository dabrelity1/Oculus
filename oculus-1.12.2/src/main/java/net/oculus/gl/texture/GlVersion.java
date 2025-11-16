package net.oculus.gl.texture;

/**
 * Minimal GL version descriptor used by texture metadata classes. The 1.12 backport does
 * not actively enforce these, but keeping the enum in place makes future ports easier.
 */
public enum GlVersion {
    GL_11,
    GL_12,
    GL_30,
    GL_31
}

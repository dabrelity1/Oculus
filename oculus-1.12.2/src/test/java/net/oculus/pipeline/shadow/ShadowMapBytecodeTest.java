package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ShadowMapBytecodeTest {
    private static final String SHADOW_MAP = "net/oculus/pipeline/shadow/ShadowMap";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String OCULUS_RENDER_SYSTEM = "net/oculus/gl/OculusRenderSystem";
    private static final String DEPTH_COPY_STRATEGY = "net/oculus/rendertarget/DepthCopyStrategy";
    private static final String SHADOW_SAMPLING_SETTINGS =
        "net/oculus/shaderpack/PackShadowDirectives$SamplingSettings";

    @Test
    public void noTranslucentsDepthCopyUsesOwnedFramebuffersAndRestoresBindings() throws Exception {
        ClassReader reader = classReader(SHADOW_MAP);
        final boolean[] foundCopyDepth = {false};
        final int[] instructionIndex = {0};
        final int[] saveCombinedFramebufferCall = {-1};
        final int[] saveReadFramebufferCall = {-1};
        final int[] saveDrawFramebufferCall = {-1};
        final int[] blitFramebufferCall = {-1};
        final int[] depthStrategyCopyCall = {-1};
        final int[] restoreFramebufferBindingsCall = {-1};
        final int[] renderSystemTextureCopyCalls = {0};
        final int[] rawBindCalls = {0};
        final int[] rawCopyCalls = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"copyDepthToNoTranslucents".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected ShadowMap.copyDepthToNoTranslucents descriptor: " + descriptor,
                    "()V".equals(descriptor));
                foundCopyDepth[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "getFramebufferBinding".equals(methodName)
                            && "()I".equals(methodDescriptor)) {
                            saveCombinedFramebufferCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "getReadFramebufferBinding".equals(methodName)
                            && "()I".equals(methodDescriptor)) {
                            saveReadFramebufferCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "getDrawFramebufferBinding".equals(methodName)
                            && "()I".equals(methodDescriptor)) {
                            saveDrawFramebufferCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "blitFramebuffer".equals(methodName)) {
                            blitFramebufferCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEINTERFACE
                            && DEPTH_COPY_STRATEGY.equals(owner)
                            && "copy".equals(methodName)) {
                            depthStrategyCopyCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "restoreFramebufferBindings".equals(methodName)
                            && "(III)V".equals(methodDescriptor)) {
                            restoreFramebufferBindingsCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "copyTexSubImage2D".equals(methodName)
                            && "(IIIIIIIII)V".equals(methodDescriptor)) {
                            renderSystemTextureCopyCalls[0]++;
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glBindTexture".equals(methodName)
                            && "(II)V".equals(methodDescriptor)) {
                            rawBindCalls[0]++;
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glCopyTexSubImage2D".equals(methodName)
                            && "(IIIIIIII)V".equals(methodDescriptor)) {
                            rawCopyCalls[0]++;
                        }
                    }
                };
            }
        }, 0);

        assertTrue("ShadowMap.copyDepthToNoTranslucents() was not found", foundCopyDepth[0]);
        assertTrue("copyDepthToNoTranslucents must save the incoming combined framebuffer binding",
            saveCombinedFramebufferCall[0] > 0);
        assertTrue("copyDepthToNoTranslucents must save the incoming read framebuffer binding",
            saveReadFramebufferCall[0] > saveCombinedFramebufferCall[0]);
        assertTrue("copyDepthToNoTranslucents must save the incoming draw framebuffer binding",
            saveDrawFramebufferCall[0] > saveReadFramebufferCall[0]);
        assertTrue("first shadow no-translucents depth copy must use the reference framebuffer blit path when available",
            blitFramebufferCall[0] > saveDrawFramebufferCall[0]);
        assertTrue("later shadow no-translucents depth copies must route through DepthCopyStrategy",
            depthStrategyCopyCall[0] > blitFramebufferCall[0]);
        assertTrue("copyDepthToNoTranslucents must restore read/draw framebuffer bindings in cleanup",
            restoreFramebufferBindingsCall[0] > depthStrategyCopyCall[0]);
        assertTrue("copyDepthToNoTranslucents must not directly call the texture-copy helper",
            renderSystemTextureCopyCalls[0] == 0);
        assertTrue("copyDepthToNoTranslucents must not raw-bind GL_TEXTURE_2D",
            rawBindCalls[0] == 0);
        assertTrue("copyDepthToNoTranslucents must not call raw glCopyTexSubImage2D directly",
            rawCopyCalls[0] == 0);
    }

    @Test
    public void shadowMipmapsUseDedicatedTextureUnitAndRestoreState() throws Exception {
        ClassReader reader = classReader(SHADOW_MAP);
        final boolean[] foundGenerateMipmaps = {false};
        final int[] instructionIndex = {0};
        final boolean[] waitingForPreviousActiveTextureStore = {false};
        final boolean[] waitingForPreviousTextureStore = {false};
        final int[] previousActiveTextureLocal = {-1};
        final int[] previousTextureLocal = {-1};
        final int[] firstActiveTextureSwitch = {-1};
        final int[] firstGenerateMipmapCall = {-1};
        final int[] restoreTextureUnitBindingCall = {-1};
        final int[] restoreActiveTextureCall = {-1};
        final int[] lastLoadedIntLocal = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"generateMipmaps".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected ShadowMap.generateMipmaps descriptor: " + descriptor,
                    "()V".equals(descriptor));
                foundGenerateMipmaps[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.ISTORE && waitingForPreviousActiveTextureStore[0]) {
                            previousActiveTextureLocal[0] = var;
                            waitingForPreviousActiveTextureStore[0] = false;
                        }
                        if (opcode == Opcodes.ISTORE && waitingForPreviousTextureStore[0]) {
                            previousTextureLocal[0] = var;
                            waitingForPreviousTextureStore[0] = false;
                        }
                        if (opcode == Opcodes.ILOAD) {
                            lastLoadedIntLocal[0] = var;
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        instructionIndex[0]++;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glGetInteger".equals(methodName)
                            && "(I)I".equals(methodDescriptor)) {
                            if (firstActiveTextureSwitch[0] < 0) {
                                waitingForPreviousActiveTextureStore[0] = true;
                            } else {
                                waitingForPreviousTextureStore[0] = true;
                            }
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "setActiveTextureUnit".equals(methodName)
                            && "(I)V".equals(methodDescriptor)) {
                            if (firstActiveTextureSwitch[0] < 0) {
                                firstActiveTextureSwitch[0] = instructionIndex[0];
                            } else if (firstGenerateMipmapCall[0] > 0
                                && lastLoadedIntLocal[0] == previousActiveTextureLocal[0]) {
                                restoreActiveTextureCall[0] = instructionIndex[0];
                            }
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && SHADOW_MAP.equals(owner)
                            && "generateMipmap".equals(methodName)
                            && ("(IL" + SHADOW_SAMPLING_SETTINGS + ";)V").equals(methodDescriptor)
                            && firstGenerateMipmapCall[0] < 0) {
                            firstGenerateMipmapCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && SHADOW_MAP.equals(owner)
                            && "restoreTextureUnitBinding".equals(methodName)
                            && "(Ljava/lang/Throwable;II)Ljava/lang/Throwable;".equals(methodDescriptor)
                            && firstGenerateMipmapCall[0] > 0) {
                            restoreTextureUnitBindingCall[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("ShadowMap.generateMipmaps() was not found", foundGenerateMipmaps[0]);
        assertTrue("generateMipmaps must save the incoming active texture unit",
            previousActiveTextureLocal[0] >= 0);
        assertTrue("generateMipmaps must save the previous GL_TEXTURE_2D binding on the mipmap unit",
            previousTextureLocal[0] >= 0);
        assertTrue("generateMipmaps must switch to a dedicated texture unit before shadow mipmap generation",
            firstActiveTextureSwitch[0] > 0 && firstActiveTextureSwitch[0] < firstGenerateMipmapCall[0]);
        assertTrue("generateMipmaps must restore the dedicated unit's previous GL_TEXTURE_2D binding",
            restoreTextureUnitBindingCall[0] > firstGenerateMipmapCall[0]);
        assertTrue("generateMipmaps must restore the incoming active texture unit",
            restoreActiveTextureCall[0] > restoreTextureUnitBindingCall[0]);
    }

    private static ClassReader classReader(String internalName) throws IOException {
        return new ClassReader(readClassBytes(internalName));
    }

    private static byte[] readClassBytes(String internalName) throws IOException {
        String resource = internalName + ".class";
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        assertNotNull("Missing class resource on test classpath: " + resource, stream);

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }
}

package net.oculus.pipeline.vertex;

public interface OculusBlockSensitiveBufferBuilder {
    void oculus$beginBlock(short block, short renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ);

    void oculus$endBlock();
}

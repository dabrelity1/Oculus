package net.oculus.gl.program;

/**
 * Placeholder compute program. Having a dedicated type keeps parity with the 1.16.5 API so
 * later steps can specialise behaviour without breaking earlier ports.
 */
public final class ComputeProgram extends Program {
    ComputeProgram(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        super(program, uniforms, samplers, images);
    }
}

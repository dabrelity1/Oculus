package net.oculus.shaderpack;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

public class CommentDirectiveParserTest {
    @Test
    public void sameCommentDirectiveUsesLastOccurrenceLikeReference() {
        Optional<CommentDirective> directive = CommentDirectiveParser.findDirective(
            "/* DRAWBUFFERS:01 */\n"
                + "void main() {}\n"
                + "/* DRAWBUFFERS:24 */\n",
            CommentDirective.Type.DRAWBUFFERS);

        assertTrue(directive.isPresent());
        assertEquals("24", directive.get().getDirective());
    }

    @Test
    public void programDirectivesApplyLastDirectiveTypeAfterPerTypeResolution() {
        ProgramDirectives directives = new ProgramDirectives(
            null,
            "composite",
            "/* DRAWBUFFERS:01 */\n"
                + "/* RENDERTARGETS:2,4 */\n"
                + "/* DRAWBUFFERS:57 */\n",
            ShaderProperties.empty(),
            null);

        assertArrayEquals(new int[] {5, 7}, directives.getDrawBuffers());
    }

    @Test(expected = NumberFormatException.class)
    public void rendertargetsTokensWithSpacesFailLikeReference() {
        new ProgramDirectives(
            null,
            "composite",
            "/* RENDERTARGETS:2, 4 */\n",
            ShaderProperties.empty(),
            null);
    }

    @Test(expected = NumberFormatException.class)
    public void emptyRendertargetsDirectiveFailsLikeReference() {
        new ProgramDirectives(
            null,
            "composite",
            "/* RENDERTARGETS: */\n",
            ShaderProperties.empty(),
            null);
    }

    @Test
    public void malformedLatestDirectiveSuppressesEarlierMatchLikeReference() {
        Optional<CommentDirective> directive = CommentDirectiveParser.findDirective(
            "/* DRAWBUFFERS:01 */\n"
                + "not a comment DRAWBUFFERS:24 */\n",
            CommentDirective.Type.DRAWBUFFERS);

        assertFalse(directive.isPresent());
    }

    @Test
    public void whitespaceBeforeColonIsNotAcceptedLikeReference() {
        Optional<CommentDirective> directive = CommentDirectiveParser.findDirective(
            "/* DRAWBUFFERS :01 */\n",
            CommentDirective.Type.DRAWBUFFERS);

        assertFalse(directive.isPresent());
    }
}

package net.oculus.shaderpack.option;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.option.values.MutableOptionValues;

public class OptionAnnotatedSourceTest {
    @Test
    public void constBooleanOptionsRequireIfdefReferenceLikeReference() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "const bool shadowHardwareFiltering = true;\n"
        );

        OptionSet unreferenced = source.getOptionSet(
            AbsolutePackPath.fromAbsolutePath("/shadow.fsh"),
            Collections.emptySet()
        );
        assertFalse(unreferenced.getBooleanOptions().containsKey("shadowHardwareFiltering"));

        OptionSet referenced = source.getOptionSet(
            AbsolutePackPath.fromAbsolutePath("/shadow.fsh"),
            Collections.singleton("shadowHardwareFiltering")
        );
        assertTrue(referenced.getBooleanOptions().containsKey("shadowHardwareFiltering"));
    }

    @Test
    public void stringConstOptionsDoNotUseBooleanReferenceGateLikeReference() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "const int shadowMapResolution = 1024; // [512 1024]\n"
        );

        OptionSet options = source.getOptionSet(
            AbsolutePackPath.fromAbsolutePath("/shadow.fsh"),
            Collections.emptySet()
        );

        assertTrue(options.getStringOptions().containsKey("shadowMapResolution"));
    }

    @Test
    public void changedStringDefineIsRewrittenToReferenceCanonicalLine() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "#define QUALITY high // Quality [low high ultra]\n"
        );
        MutableOptionValues values = valuesFor(source, Collections.emptySet());
        values.setStringValue("QUALITY", "low");

        assertEquals(
            "#define QUALITY low // OptionAnnotatedSource: Changed option",
            source.asTransform(values).transform(0, source.getLines().get(0))
        );
    }

    @Test
    public void unchangedStringDefineLineIsPreservedLikeReference() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "#define QUALITY high // Quality [low high ultra]\n"
        );
        MutableOptionValues values = valuesFor(source, Collections.emptySet());

        assertEquals(
            "#define QUALITY high // Quality [low high ultra]",
            source.asTransform(values).transform(0, source.getLines().get(0))
        );
    }

    @Test
    public void constEditsQuoteReplacementValuesLikeReference() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "const int shadowMapResolution = 1024; // [512 1024]\n"
        );
        MutableOptionValues values = valuesFor(source, Collections.emptySet());
        values.setStringValue("shadowMapResolution", "$2048");

        assertEquals(
            "const int shadowMapResolution = $2048; // [512 1024]",
            source.asTransform(values).transform(0, source.getLines().get(0))
        );
    }

    @Test
    public void commentedBooleanDefineUsesReferenceTriStateEditing() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "  //#define SHADOWS // Shadow toggle\n"
        );
        MutableOptionValues values = valuesFor(source, Collections.singleton("SHADOWS"));
        values.setBooleanValue("SHADOWS", true);

        assertEquals(
            "#define SHADOWS // Shadow toggle",
            source.asTransform(values).transform(0, source.getLines().get(0))
        );
    }

    @Test
    public void defaultFalseBooleanDefineIsStillSetThroughReferenceCommentPath() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "//#define SHADOWS // Shadow toggle\n"
        );
        MutableOptionValues values = valuesFor(source, Collections.singleton("SHADOWS"));

        assertEquals(
            "////#define SHADOWS // Shadow toggle",
            source.asTransform(values).transform(0, source.getLines().get(0))
        );
    }

    @Test
    public void unchangedConstBooleanLineIsPreservedLikeReference() {
        OptionAnnotatedSource source = new OptionAnnotatedSource(
            "const bool shadowHardwareFiltering = true; // hardware filtering\n"
        );
        MutableOptionValues values = valuesFor(source, Collections.singleton("shadowHardwareFiltering"));

        assertEquals(
            "const bool shadowHardwareFiltering = true; // hardware filtering",
            source.asTransform(values).transform(0, source.getLines().get(0))
        );
    }

    private static MutableOptionValues valuesFor(OptionAnnotatedSource source, Set<String> booleanReferences) {
        return new MutableOptionValues(source.getOptionSet(
            AbsolutePackPath.fromAbsolutePath("/shader.fsh"),
            booleanReferences
        ));
    }
}

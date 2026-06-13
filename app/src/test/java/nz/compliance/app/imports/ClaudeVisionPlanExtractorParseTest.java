package nz.compliance.app.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeVisionPlanExtractorParseTest {

    private final ClaudeVisionPlanExtractor extractor = new ClaudeVisionPlanExtractor("");

    @Test
    void parsesJsonIntoAnExtraction() {
        String json = """
            {"rooms":[{"label":"Office","occupancyTypeGuess":"WB",
                       "polygonPx":[{"x":0,"y":0},{"x":10,"y":0},{"x":10,"y":10}],"confidence":0.9}],
             "doors":[{"positionPx":[{"x":0,"y":4},{"x":0,"y":6}],"connectsRoomLabels":["Office"],
                       "exitGuess":true,"clearWidthMmGuess":1200,"confidence":0.8}],
             "scaleGuess":{"metresPerPixel":0.05,"source":"scale-bar","confidence":0.7},
             "warnings":[]}""";

        PlanExtraction ex = extractor.parse(json);

        assertThat(ex.rooms()).hasSize(1);
        assertThat(ex.rooms().get(0).label()).isEqualTo("Office");
        assertThat(ex.doors().get(0).exitGuess()).isTrue();
        assertThat(ex.scaleGuess().metresPerPixel()).isEqualTo(0.05);
    }

    @Test
    void stripsMarkdownFences() {
        String fenced = "```json\n{\"rooms\":[],\"doors\":[],\"scaleGuess\":null,\"warnings\":[]}\n```";
        assertThat(extractor.parse(fenced).rooms()).isEmpty();
    }

    @Test
    void malformedOutputDegradesToAWarning() {
        PlanExtraction ex = extractor.parse("sorry, I cannot read this");
        assertThat(ex.rooms()).isEmpty();
        assertThat(ex.warnings()).isNotEmpty();
    }

    @Test
    void aNullArrayElementDegradesToAWarningRatherThanCrashing() {
        // Guards review finding #6: a JSON null inside polygonPx trips List.copyOf in the
        // record's compact constructor; parse() must contain that, not propagate an NPE.
        String json = """
            {"rooms":[{"label":"X","occupancyTypeGuess":"WB",
                       "polygonPx":[{"x":0,"y":0},null,{"x":5,"y":5}],"confidence":0.5}],
             "doors":[],"scaleGuess":null,"warnings":[]}""";

        PlanExtraction ex = extractor.parse(json);

        assertThat(ex.rooms()).isEmpty();
        assertThat(ex.warnings()).isNotEmpty();
    }

    @Test
    void stripsASingleLineFence() {
        String fenced = "```json {\"rooms\":[],\"doors\":[],\"scaleGuess\":null,\"warnings\":[]} ```";
        PlanExtraction ex = extractor.parse(fenced);
        assertThat(ex.warnings()).isEmpty();   // parsed cleanly, not degraded to a warning
        assertThat(ex.rooms()).isEmpty();
    }

    @Test
    void stripsAFenceWithNoTrailingNewline() {
        String fenced = "```json\n{\"rooms\":[],\"doors\":[],\"scaleGuess\":null,\"warnings\":[]}```";
        PlanExtraction ex = extractor.parse(fenced);
        assertThat(ex.warnings()).isEmpty();   // parsed cleanly, not degraded to a warning
    }

    @Test
    void aTruncatedReplySurfacesACutOffWarningInsteadOfSilentlyEmptying() {
        // finishReason==LENGTH means the model hit maxTokens mid-JSON. The partial text won't
        // parse, but the user must learn WHY (plan too large), not get a silent empty plan.
        PlanExtraction ex = extractor.interpret("{\"rooms\":[{\"label\":\"A\",", true);
        assertThat(ex.rooms()).isEmpty();
        assertThat(ex.warnings()).anyMatch(w -> w.toLowerCase().contains("cut off"));
    }

    @Test
    void aTruncatedReplyKeepsWhateverParsedAndStillFlagsTheCutOff() {
        // If a complete-enough prefix did parse, keep it — but still warn it may be incomplete.
        String json = """
            {"rooms":[{"label":"A","occupancyTypeGuess":"","polygonPx":[{"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":1}],"confidence":0.5}],
             "doors":[],"scaleGuess":null,"warnings":[]}""";
        PlanExtraction ex = extractor.interpret(json, true);
        assertThat(ex.rooms()).hasSize(1);
        assertThat(ex.warnings()).anyMatch(w -> w.toLowerCase().contains("cut off"));
    }

    @Test
    void aCompleteReplyInterpretsExactlyLikeParse() {
        String json = "{\"rooms\":[],\"doors\":[],\"scaleGuess\":null,\"warnings\":[]}";
        PlanExtraction ex = extractor.interpret(json, false);
        assertThat(ex.warnings()).isEmpty();   // no truncation note added on a clean, complete reply
    }
}

package nz.compliance.engine.check;

import nz.compliance.engine.model.*;
import nz.compliance.engine.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceEngineTest {

    private static Space rect(String id, double x0, double y0, double x1, double y1) {
        return new Space(id, id, "WB", List.of(
                new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1)));
    }

    private final ComplianceEngine engine = new ComplianceEngine();
    private final BuildingContext ctx = new BuildingContext("WB", true, 3.0);

    // open path <= 10 m, and >= 2 escape routes
    private final RuleSet rules = new RuleSet("nz", "v1", List.of(
            new Rule("openpath", "C/AS2 open path", "Open path", ParameterKey.OPEN_PATH_LENGTH, Comparator.LTE, 10, Severity.ERROR, Set.of()),
            new Rule("exits", "C/AS2 escape routes", "Escape routes", ParameterKey.EXIT_COUNT, Comparator.GTE, 2, Severity.ERROR, Set.of())));

    @Test
    void flagsOpenPathAndExitCountViolations() {
        // s1(0,0-10,10) with the only exit; s2(10,0-20,10) routes through s1 -> 15 m
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s2", 10, 0, 20, 10)),
                List.of(
                        new Door("d12", "s1", "s2", List.of(new Point(10, 4), new Point(10, 6)), 1000, false),
                        new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));

        CheckResult r = engine.check(doc, ctx, rules);

        assertThat(r.blocked()).isFalse();
        // s2 open path 15 > 10 -> violation; exit count 1 < 2 -> violation
        assertThat(r.violations()).extracting(Violation::ruleId).contains("openpath", "exits");
        Violation openPath = r.violations().stream().filter(v -> v.ruleId().equals("openpath")).findFirst().orElseThrow();
        assertThat(openPath.spaceId()).isEqualTo("s2");
        assertThat(openPath.pathNodeIds()).containsExactly("s2", "s1", "EXTERIOR");
        // s1 open path 5 <= 10 -> passed
        assertThat(r.passed()).anyMatch(o -> o.ruleId().equals("openpath") && "s1".equals(o.spaceId()));
    }

    @Test
    void blocksWhenNoExits() {
        GeometryDoc doc = new GeometryDoc(1, List.of(rect("s1", 0, 0, 10, 10)), List.of());
        CheckResult r = engine.check(doc, ctx, rules);
        assertThat(r.blocked()).isTrue();
        assertThat(r.blockMessage()).contains("exit");
    }

    @Test
    void doorlessSpaceIsNotEvaluated_notAViolation() {
        // s3 is a valid room with NO door -> incomplete model -> NOT_EVALUATED (spec §11),
        // not a 'no means of escape' violation.
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s3", 100, 100, 110, 110)),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        CheckResult r = engine.check(doc, ctx, rules);

        assertThat(r.violations()).noneMatch(v -> v.ruleId().equals("structural.no-egress") && "s3".equals(v.spaceId()));
        assertThat(r.notEvaluated()).anyMatch(o -> o.ruleId().equals("structural.no-egress") && "s3".equals(o.spaceId()));
    }

    @Test
    void connectedButExitlessSpaceIsNoMeansOfEscape() {
        // s3<->s4 form a connected component with a door but NO exit -> real violation (spec §11).
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s3", 100, 100, 110, 110), rect("s4", 110, 100, 120, 110)),
                List.of(
                        new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true),
                        new Door("d34", "s3", "s4", List.of(new Point(110, 104), new Point(110, 106)), 900, false)));
        CheckResult r = engine.check(doc, ctx, rules);

        assertThat(r.violations()).anyMatch(v -> v.ruleId().equals("structural.no-egress") && "s3".equals(v.spaceId()));
        assertThat(r.violations()).anyMatch(v -> v.ruleId().equals("structural.no-egress") && "s4".equals(v.spaceId()));
    }

    @Test
    void degenerateSpaceIsNotEvaluated_notAViolation() {
        // bow-tie passes Plan 2's structural 422 gate but is self-intersecting -> NOT_EVALUATED, never a violation.
        Space bowtie = new Space("bad", "Bad", "WB", List.of(
                new Point(20, 20), new Point(30, 30), new Point(30, 20), new Point(20, 30)));
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), bowtie),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        CheckResult r = engine.check(doc, ctx, rules);

        assertThat(r.violations()).noneMatch(v -> "bad".equals(v.spaceId()));
        assertThat(r.notEvaluated()).anyMatch(o -> o.ruleId().equals("structural.no-egress") && "bad".equals(o.spaceId()));
    }
}

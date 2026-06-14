package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExtractionScorerTest {

    private final ExtractionScorer scorer = new ExtractionScorer();

    private static List<Point> square(double x0, double y0, double x1, double y1) {
        return List.of(new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1));
    }

    private static ExtractedRoom predRoom(String label, double x0, double y0, double x1, double y1) {
        return new ExtractedRoom(label, "", square(x0, y0, x1, y1), 0.9);
    }

    private static GoldPlan.GoldRoom goldRoom(String label, double x0, double y0, double x1, double y1) {
        return new GoldPlan.GoldRoom(label, square(x0, y0, x1, y1));
    }

    private static PlanExtraction pred(List<ExtractedRoom> rooms, List<ExtractedDoor> doors, ScaleGuess scale) {
        return new PlanExtraction(rooms, doors, scale, List.of());
    }

    private static GoldPlan gold(List<GoldPlan.GoldRoom> rooms, List<GoldPlan.GoldDoor> doors,
                                 Double scale, Boolean scoreScale) {
        return new GoldPlan(rooms, doors, scale, scoreScale);
    }

    @Test
    void identicalRoomsScorePerfectRecallPrecisionAndIoU() {
        ExtractionScorer.ScoreReport r = scorer.score(
                pred(List.of(predRoom("Kitchen", 0, 0, 10, 10)), List.of(), null),
                gold(List.of(goldRoom("Kitchen", 0, 0, 10, 10)), List.of(), null, true));

        assertThat(r.roomRecall()).isEqualTo(1.0);
        assertThat(r.roomPrecision()).isEqualTo(1.0);
        assertThat(r.meanIoU()).isCloseTo(1.0, within(1e-6));
        assertThat(r.labelAccuracy()).isEqualTo(1.0);
    }

    @Test
    void disjointRoomsDoNotMatch() {
        ExtractionScorer.ScoreReport r = scorer.score(
                pred(List.of(predRoom("A", 100, 100, 110, 110)), List.of(), null),
                gold(List.of(goldRoom("A", 0, 0, 10, 10)), List.of(), null, true));

        assertThat(r.matchedRooms()).isZero();
        assertThat(r.roomRecall()).isZero();
        assertThat(r.meanIoU()).isZero();
    }

    @Test
    void roomBelowIoUThresholdDoesNotMatch() {
        // gold 0..10, pred 5..15 -> intersection 50, union 150, IoU 0.33 < 0.5
        assertThat(scorer.score(
                pred(List.of(predRoom("A", 5, 0, 15, 10)), List.of(), null),
                gold(List.of(goldRoom("A", 0, 0, 10, 10)), List.of(), null, true)).matchedRooms()).isZero();
    }

    @Test
    void roomAboveIoUThresholdMatches() {
        // gold 0..10, pred 1..11 -> intersection 90, union 110, IoU 0.82 >= 0.5
        assertThat(scorer.score(
                pred(List.of(predRoom("A", 1, 0, 11, 10)), List.of(), null),
                gold(List.of(goldRoom("A", 0, 0, 10, 10)), List.of(), null, true)).matchedRooms()).isEqualTo(1);
    }

    @Test
    void labelAccuracyCountsOnlyMatchedRoomsWithTheSameLabel() {
        ExtractionScorer.ScoreReport r = scorer.score(
                pred(List.of(predRoom("Bedroom", 0, 0, 10, 10)), List.of(), null),
                gold(List.of(goldRoom("Kitchen", 0, 0, 10, 10)), List.of(), null, true));

        assertThat(r.matchedRooms()).isEqualTo(1);   // geometry matches
        assertThat(r.labelAccuracy()).isZero();      // label does not
    }

    @Test
    void scaleWithinToleranceIsOk() {
        ExtractionScorer.ScoreReport r = scorer.score(
                pred(List.of(), List.of(), new ScaleGuess(0.052, "scale-bar", 0.9)),
                gold(List.of(), List.of(), 0.05, true));

        assertThat(r.scaleOk()).isTrue();
        assertThat(r.scaleRelError()).isCloseTo(0.04, within(1e-6));
    }

    @Test
    void scaleOutsideToleranceFails() {
        assertThat(scorer.score(
                pred(List.of(), List.of(), new ScaleGuess(0.07, "scale-bar", 0.9)),
                gold(List.of(), List.of(), 0.05, true)).scaleOk()).isFalse();
    }

    @Test
    void expectedNullScaleFailsWhenAValueIsGuessed() {
        assertThat(scorer.score(
                pred(List.of(), List.of(), new ScaleGuess(0.05, "other", 0.5)),
                gold(List.of(), List.of(), null, true)).scaleOk()).isFalse();
    }

    @Test
    void expectedNullScaleIsOkWhenNullGuessed() {
        assertThat(scorer.score(
                pred(List.of(), List.of(), null),
                gold(List.of(), List.of(), null, true)).scaleOk()).isTrue();
    }

    @Test
    void scaleNotScoredWhenGoldOptsOut() {
        // scoreScale=false -> scale dimension skipped, never penalised
        ExtractionScorer.ScoreReport r = scorer.score(
                pred(List.of(), List.of(), new ScaleGuess(0.05, "other", 0.5)),
                gold(List.of(), List.of(), null, false));

        assertThat(r.scaleScored()).isFalse();
        assertThat(r.scaleOk()).isTrue();
    }

    @Test
    void doorsMatchByMidpointProximity() {
        ExtractedDoor near = new ExtractedDoor(List.of(new Point(1, 41), new Point(1, 59)),
                List.of(), false, null, 0.5);
        GoldPlan.GoldDoor g = new GoldPlan.GoldDoor(List.of(new Point(0, 40), new Point(0, 60)), false);

        ExtractionScorer.ScoreReport r = scorer.score(
                pred(List.of(), List.of(near), null), gold(List.of(), List.of(g), null, true));

        assertThat(r.matchedDoors()).isEqualTo(1);
        assertThat(r.doorRecall()).isEqualTo(1.0);
    }

    @Test
    void farDoorsDoNotMatch() {
        ExtractedDoor far = new ExtractedDoor(List.of(new Point(500, 500), new Point(500, 520)),
                List.of(), false, null, 0.5);
        GoldPlan.GoldDoor g = new GoldPlan.GoldDoor(List.of(new Point(0, 40), new Point(0, 60)), false);

        assertThat(scorer.score(
                pred(List.of(), List.of(far), null), gold(List.of(), List.of(g), null, true)).matchedDoors()).isZero();
    }
}

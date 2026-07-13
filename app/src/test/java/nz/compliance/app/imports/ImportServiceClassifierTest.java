package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classifier seam. The behaviour under test is mostly about what we refuse to do:
 * suggest a type without a scale, overwrite what the plan already says, or fail an import
 * because a model was unreachable.
 */
class ImportServiceClassifierTest {

    private static final List<Point> SQUARE =
            List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10));

    @Test
    void fillsBlankOccupancyTypeWhenScaleIsKnown() {
        ImportDraft draft = service(
                draftWith(new ScaleGuess(0.05, "scale-bar", 0.9), space("room-1", "")),
                (geo, scale) -> Map.of("room-1", "CA")
        ).importFrom(new byte[0]);

        assertThat(occupancyOf(draft, "room-1")).isEqualTo("CA");
        assertThat(draft.warnings()).anyMatch(w -> w.contains("Review each before confirming"));
    }

    @Test
    void doesNotClassifyWithoutAScale() {
        // Draft geometry is in pixels. Without a scale there is no way to turn a pixel area into
        // m², and an occupancy type inferred from a meaningless area would change occupant load.
        AtomicInteger calls = new AtomicInteger();
        ImportDraft draft = service(
                draftWith(null, space("room-1", "")),
                (geo, scale) -> {
                    calls.incrementAndGet();
                    return Map.of("room-1", "CA");
                }
        ).importFrom(new byte[0]);

        assertThat(calls).hasValue(0);
        assertThat(occupancyOf(draft, "room-1")).isEmpty();
    }

    @Test
    void doesNotClassifyOnANonPositiveScale() {
        AtomicInteger calls = new AtomicInteger();
        service(
                draftWith(new ScaleGuess(0, "scale-bar", 0.1), space("room-1", "")),
                (geo, scale) -> {
                    calls.incrementAndGet();
                    return Map.of("room-1", "CA");
                }
        ).importFrom(new byte[0]);

        assertThat(calls).hasValue(0);
    }

    @Test
    void neverOverwritesAnOccupancyTypeTheVisionExtractorSupplied() {
        ImportDraft draft = service(
                draftWith(new ScaleGuess(0.05, "scale-bar", 0.9), space("room-1", "WB")),
                (geo, scale) -> Map.of("room-1", "CA")
        ).importFrom(new byte[0]);

        assertThat(occupancyOf(draft, "room-1")).isEqualTo("WB");
        assertThat(draft.warnings()).isEmpty();
    }

    @Test
    void anUnavailableClassifierDoesNotFailTheImport() {
        ImportDraft draft = service(
                draftWith(new ScaleGuess(0.05, "scale-bar", 0.9), space("room-1", "")),
                (geo, scale) -> Map.of()  // what HttpRoomTypeClassifier returns when the service is down
        ).importFrom(new byte[0]);

        assertThat(draft.draftGeometryPx().spaces()).hasSize(1);
        assertThat(occupancyOf(draft, "room-1")).isEmpty();
        assertThat(draft.warnings()).isEmpty();
    }

    private static Space space(String id, String occupancyType) {
        return new Space(id, "Room", occupancyType, SQUARE);
    }

    private static ImportDraft draftWith(ScaleGuess scale, Space... spaces) {
        return new ImportDraft("", 100, 100,
                new GeometryDoc(1, List.of(spaces), List.of()), scale, List.of());
    }

    private static String occupancyOf(ImportDraft draft, String spaceId) {
        return draft.draftGeometryPx().spaces().stream()
                .filter(s -> s.id().equals(spaceId))
                .findFirst().orElseThrow()
                .occupancyType();
    }

    /** ImportService with the render/extract/assemble stages stubbed to yield a fixed draft. */
    private static ImportService service(ImportDraft draft, RoomTypeClassifier classifier) {
        PlanImageRenderer renderer = new PlanImageRenderer() {
            @Override
            public RenderedImage render(byte[] bytes) {
                return new RenderedImage(new byte[0], draft.imageWidthPx(), draft.imageHeightPx());
            }
        };
        VisionPlanExtractor extractor =
                image -> new PlanExtraction(List.of(), List.of(), draft.scaleGuess(), List.of());
        ImportDraftAssembler assembler = new ImportDraftAssembler() {
            @Override
            public ImportDraft assemble(RenderedImage image, PlanExtraction extraction) {
                return draft;
            }
        };
        return new ImportService(renderer, extractor, assembler, classifier);
    }
}

package nz.compliance.app.imports;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportDraftAssemblerTest {

    private final ImportDraftAssembler assembler = new ImportDraftAssembler();
    private final RenderedImage image = new RenderedImage(new byte[]{1, 2, 3}, 200, 100);

    private static ExtractedRoom room(String label, double x0, double y0, double x1, double y1) {
        return new ExtractedRoom(label, "WB", List.of(
                new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1)), 0.9);
    }

    @Test
    void synthesizesIdsAndMapsAnExitDoorToTheNamedRoom() {
        PlanExtraction ex = new PlanExtraction(
                List.of(room("Office", 0, 0, 100, 100), room("Lobby", 100, 0, 200, 100)),
                List.of(new ExtractedDoor(List.of(new Point(0, 40), new Point(0, 60)),
                        List.of("Office"), true, 1200.0, 0.8)),
                new ScaleGuess(0.05, "scale-bar", 0.7), List.of());

        ImportDraft draft = assembler.assemble(image, ex);
        GeometryDoc g = draft.draftGeometryPx();

        assertThat(g.spaces()).extracting(Space::id).containsExactly("room-1", "room-2");
        assertThat(g.spaces()).extracting(Space::name).containsExactly("Office", "Lobby");
        assertThat(g.doors()).hasSize(1);
        Door d = g.doors().get(0);
        assertThat(d.id()).isEqualTo("door-1");
        assertThat(d.fromSpaceId()).isEqualTo("room-1");
        assertThat(d.toSpaceId()).isNull();           // exit -> discharges outside
        assertThat(d.exit()).isTrue();
        assertThat(d.clearWidthMillimetres()).isEqualTo(1200.0);
        assertThat(draft.scaleGuess().metresPerPixel()).isEqualTo(0.05);
        assertThat(draft.imageWidthPx()).isEqualTo(200);
        assertThat(draft.backdropPngBase64()).isNotBlank();
    }

    @Test
    void unlabeledDoorAttachesToNearestRoomCentroid() {
        PlanExtraction ex = new PlanExtraction(
                List.of(room("Office", 0, 0, 100, 100), room("Lobby", 100, 0, 200, 100)),
                // door midpoint x=150 is nearer Lobby's centroid (150,50) than Office's (50,50)
                List.of(new ExtractedDoor(List.of(new Point(150, 40), new Point(150, 60)),
                        List.of(), false, null, 0.5)),
                null, List.of());

        Door d = assembler.assemble(image, ex).draftGeometryPx().doors().get(0);

        assertThat(d.fromSpaceId()).isEqualTo("room-2");   // Lobby
        assertThat(d.clearWidthMillimetres()).isEqualTo(0.0); // unknown -> 0, user sets it
    }

    @Test
    void dropsDegenerateRoomsAndDoorsWithAWarning() {
        PlanExtraction ex = new PlanExtraction(
                List.of(new ExtractedRoom("Bad", "WB", List.of(new Point(0, 0), new Point(1, 1)), 0.2),
                        room("Office", 0, 0, 100, 100)),
                List.of(new ExtractedDoor(List.of(new Point(0, 40)), List.of("Office"), false, null, 0.3)),
                null, List.of());

        ImportDraft draft = assembler.assemble(image, ex);

        assertThat(draft.draftGeometryPx().spaces()).extracting(Space::name).containsExactly("Office");
        assertThat(draft.draftGeometryPx().doors()).isEmpty();
        assertThat(draft.warnings()).isNotEmpty();
    }

    @Test
    void dropsADoorWhenThereIsNoRoomToAttachItTo() {
        PlanExtraction ex = new PlanExtraction(
                List.of(),   // no rooms at all
                List.of(new ExtractedDoor(List.of(new Point(10, 10), new Point(10, 30)),
                        List.of(), false, null, 0.4)),
                null, List.of());

        ImportDraft draft = assembler.assemble(image, ex);

        assertThat(draft.draftGeometryPx().doors()).isEmpty();
        assertThat(draft.warnings()).anyMatch(w -> w.toLowerCase().contains("no room"));
    }

    @Test
    void warnsWhenADoorIsAttachedByProximityBecauseNoLabelMatched() {
        PlanExtraction ex = new PlanExtraction(
                List.of(room("Office", 0, 0, 100, 100), room("Lobby", 100, 0, 200, 100)),
                // label matches no room -> silent geometric fallback today; must warn instead.
                // midpoint x=150 is nearest Lobby's centroid (150,50).
                List.of(new ExtractedDoor(List.of(new Point(150, 40), new Point(150, 60)),
                        List.of("Mystery Room"), false, null, 0.5)),
                null, List.of());

        ImportDraft draft = assembler.assemble(image, ex);
        Door d = draft.draftGeometryPx().doors().get(0);

        assertThat(d.fromSpaceId()).isEqualTo("room-2");   // still attaches to nearest (Lobby)
        assertThat(draft.warnings())
                .anyMatch(w -> w.contains("door-1") && w.toLowerCase().contains("nearest"));
    }

    @Test
    void warnsWhenANonExitDoorConnectsOnlyOneRoom() {
        PlanExtraction ex = new PlanExtraction(
                List.of(room("Office", 0, 0, 100, 100)),
                List.of(new ExtractedDoor(List.of(new Point(0, 40), new Point(0, 60)),
                        List.of("Office"), false, null, 0.5)),
                null, List.of());

        ImportDraft draft = assembler.assemble(image, ex);
        Door d = draft.draftGeometryPx().doors().get(0);

        assertThat(d.toSpaceId()).isNull();   // no second room -> becomes an exterior edge
        assertThat(d.exit()).isFalse();
        assertThat(draft.warnings()).anyMatch(w -> w.contains("door-1") && w.toLowerCase().contains("one room"));
    }
}

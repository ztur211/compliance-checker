package nz.compliance.engine.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryDocTest {

    private static Space square(String id) {
        return new Space(id, id, "WB", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
    }

    @Test
    void validate_passesForWellFormedDoc() {
        var doc = new GeometryDoc(1,
                List.of(square("s1")),
                List.of(new Door("d1", "s1", null,
                        List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));

        assertThat(doc.validationErrors()).isEmpty();
    }

    @Test
    void validate_rejectsSpaceWithFewerThanThreePoints() {
        var doc = new GeometryDoc(1,
                List.of(new Space("s1", "s1", "WB", List.of(new Point(0, 0), new Point(1, 1)))),
                List.of());

        assertThat(doc.validationErrors()).anyMatch(e -> e.contains("s1") && e.contains("at least 3"));
    }

    @Test
    void validate_rejectsDoorReferencingUnknownSpace() {
        var doc = new GeometryDoc(1,
                List.of(square("s1")),
                List.of(new Door("d1", "ghost", null,
                        List.of(new Point(0, 4), new Point(0, 6)), 1200, false)));

        assertThat(doc.validationErrors()).anyMatch(e -> e.contains("d1") && e.contains("ghost"));
    }

    @Test
    void constructor_isNullSafeForLists() {
        var doc = new GeometryDoc(1, null, null);
        assertThat(doc.spaces()).isEmpty();
        assertThat(doc.doors()).isEmpty();
    }
}

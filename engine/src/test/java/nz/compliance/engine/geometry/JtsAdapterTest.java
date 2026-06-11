package nz.compliance.engine.geometry;

import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JtsAdapterTest {

    private static Space space(String id, List<Point> pts) {
        return new Space(id, id, "WB", pts);
    }

    @Test
    void area_ofTenByTenSquare_is100() {
        Space s = space("s1", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
        assertThat(JtsAdapter.areaSquareMetres(s)).isEqualTo(100.0);
    }

    @Test
    void isValid_trueForSimpleSquare() {
        Space s = space("s1", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
        assertThat(JtsAdapter.isValid(s)).isTrue();
    }

    @Test
    void isValid_falseForSelfIntersectingBowtie() {
        // bow-tie: edges cross -> invalid polygon
        Space s = space("s1", List.of(
                new Point(0, 0), new Point(10, 10), new Point(10, 0), new Point(0, 10)));
        assertThat(JtsAdapter.isValid(s)).isFalse();
    }

    @Test
    void isValid_falseForFewerThanThreePoints() {
        Space s = space("s1", List.of(new Point(0, 0), new Point(1, 1)));
        assertThat(JtsAdapter.isValid(s)).isFalse();
    }
}

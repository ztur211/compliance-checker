package nz.compliance.engine.geometry;

import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JtsAdapterCentroidTest {

    @Test
    void centroid_ofSquareIsCentre() {
        Space s = new Space("s1", "s1", "WB", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
        Point c = JtsAdapter.centroid(s);
        assertThat(c.x()).isCloseTo(5.0, within(1e-9));
        assertThat(c.y()).isCloseTo(5.0, within(1e-9));
    }
}

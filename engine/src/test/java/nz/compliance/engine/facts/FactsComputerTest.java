package nz.compliance.engine.facts;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FactsComputerTest {

    private static Space square(String id, String type, double side) {
        return new Space(id, id, type, List.of(
                new Point(0, 0), new Point(side, 0), new Point(side, side), new Point(0, side)));
    }

    @Test
    void computesAreaAndOccupantLoadPerSpace() {
        // 10x10 WB space = 100 m², density 10 m²/person => 10 occupants
        GeometryDoc doc = new GeometryDoc(1, List.of(square("s1", "WB", 10)), List.of());

        PlanFacts facts = FactsComputer.compute(doc);

        assertThat(facts.spaces()).hasSize(1);
        assertThat(facts.spaces().get(0).areaSquareMetres()).isEqualTo(100.0);
        assertThat(facts.spaces().get(0).occupantLoad()).isCloseTo(10.0, within(1e-9));
        assertThat(facts.totalOccupantLoad()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void countsExitDoorsAndSumsExitWidth() {
        GeometryDoc doc = new GeometryDoc(1,
                List.of(square("s1", "WB", 10)),
                List.of(
                        new Door("d1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true),
                        new Door("d2", "s1", null, List.of(new Point(10, 4), new Point(10, 6)), 900, true),
                        new Door("d3", "s1", null, List.of(new Point(4, 0), new Point(6, 0)), 800, false)));

        PlanFacts facts = FactsComputer.compute(doc);

        assertThat(facts.exitDoorCount()).isEqualTo(2);
        assertThat(facts.totalExitWidthMillimetres()).isEqualTo(2100.0);
    }
}

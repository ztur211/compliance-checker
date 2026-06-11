package nz.compliance.engine.facts;

import nz.compliance.engine.geometry.JtsAdapter;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Space;

import java.util.ArrayList;
import java.util.List;

/** Computes raw spatial facts from a geometry document. Pure and deterministic. */
public final class FactsComputer {

    private FactsComputer() {
    }

    public static PlanFacts compute(GeometryDoc doc) {
        List<SpaceFacts> spaceFacts = new ArrayList<>();
        double totalOccupants = 0;
        for (Space s : doc.spaces()) {
            double area = JtsAdapter.areaSquareMetres(s);
            double occupants = area / OccupantDensity.squareMetresPerPerson(s.occupancyType());
            spaceFacts.add(new SpaceFacts(s.id(), area, occupants));
            totalOccupants += occupants;
        }

        int exitCount = 0;
        double exitWidth = 0;
        for (Door d : doc.doors()) {
            if (d.exit()) {
                exitCount++;
                exitWidth += d.clearWidthMillimetres();
            }
        }

        return new PlanFacts(spaceFacts, totalOccupants, exitCount, exitWidth);
    }
}

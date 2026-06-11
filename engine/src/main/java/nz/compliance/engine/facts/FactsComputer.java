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
            // Only trust JTS area/occupant load for topologically valid polygons.
            // A self-intersecting ring's getArea() is silently wrong (e.g. a
            // figure-eight reports the sum of its lobes), and areaSquareMetres()
            // throws on a <3-point ring. Flag invalid spaces so the rules layer
            // can mark them "not evaluated" instead of consuming bogus facts.
            boolean valid = JtsAdapter.isValid(s);
            double area = valid ? JtsAdapter.areaSquareMetres(s) : 0.0;
            double occupants = valid ? area / OccupantDensity.squareMetresPerPerson(s.occupancyType()) : 0.0;
            spaceFacts.add(new SpaceFacts(s.id(), valid, area, occupants));
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

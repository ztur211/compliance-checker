package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;

import java.util.Map;

/**
 * Suggests an occupancy type for each room of an imported plan.
 *
 * <p>Occupancy type is not cosmetic: {@code OccupantDensity} turns it into m²/person, which sets
 * occupant load, which sets the egress requirement. A wrong type changes a compliance verdict, so
 * a suggestion here is exactly that - a suggestion, pre-filled for a human to review and approve
 * before it ever reaches a check. This is the same authoring-time/check-time split the LLM rule
 * codification uses, and for the same reason: a check must stay reproducible from (geometry,
 * building context, rule set version) alone.
 *
 * <p>Implementations must fail soft. An import that cannot reach the classifier is an import with
 * unfilled occupancy types, which the reviewer fills in by hand. It is never a failed import.
 */
public interface RoomTypeClassifier {

    /**
     * @param geometryPx draft geometry in IMAGE PIXELS
     * @param metresPerPixel a positive, resolved scale; callers must not invent one
     * @return space id -> occupancy type, containing only rooms the classifier is confident about.
     *         An empty map is a valid answer and means "no opinion", not "failure".
     */
    Map<String, String> classify(GeometryDoc geometryPx, double metresPerPixel);
}

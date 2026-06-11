package nz.compliance.engine.facts;

import java.util.Map;

/**
 * Floor area per person (m²/person) keyed by occupancy/use type.
 * v1 values are ILLUSTRATIVE placeholders pending NZBC C/AS2 confirmation.
 */
public final class OccupantDensity {

    private static final Map<String, Double> SQM_PER_PERSON = Map.of(
            "WB", 10.0,  // working / business (illustrative)
            "CA", 1.0    // crowd activity (illustrative)
    );

    private static final double DEFAULT_SQM_PER_PERSON = 10.0;

    private OccupantDensity() {
    }

    public static double squareMetresPerPerson(String occupancyType) {
        return SQM_PER_PERSON.getOrDefault(occupancyType, DEFAULT_SQM_PER_PERSON);
    }
}

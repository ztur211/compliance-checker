package nz.compliance.engine.facts;

/**
 * Per-space computed facts. {@code valid} is the JTS topology check (a simple,
 * non-self-intersecting polygon with at least 3 points); when {@code false} the
 * area / occupant load are not physically meaningful (reported as 0) and the
 * rules layer (Plan 5) should surface the space as "not evaluated" rather than
 * treat the numbers as real. {@code occupantLoad} is raw (un-rounded); rounding
 * is a rule concern.
 */
public record SpaceFacts(String spaceId, boolean valid, double areaSquareMetres, double occupantLoad) {
}

package nz.compliance.engine.model;

/** Building characteristics that drive rule applicability and thresholds (NZ). */
public record BuildingContext(String riskGroup, boolean sprinklered, Double escapeHeightMetres) {
}

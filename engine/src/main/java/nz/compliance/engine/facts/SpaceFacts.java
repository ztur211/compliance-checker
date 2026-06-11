package nz.compliance.engine.facts;

/** Per-space computed facts. occupantLoad is raw (un-rounded); rounding is a rule concern. */
public record SpaceFacts(String spaceId, double areaSquareMetres, double occupantLoad) {
}

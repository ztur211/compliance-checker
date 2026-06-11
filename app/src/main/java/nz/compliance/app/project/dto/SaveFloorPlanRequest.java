package nz.compliance.app.project.dto;

import jakarta.validation.constraints.NotBlank;
import nz.compliance.engine.model.GeometryDoc;

public record SaveFloorPlanRequest(@NotBlank String name, String riskGroup, Boolean sprinklered,
                                   Double escapeHeightMetres, GeometryDoc geometry) {
}

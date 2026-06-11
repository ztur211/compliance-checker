package nz.compliance.app.project.dto;

import nz.compliance.app.project.FloorPlan;
import nz.compliance.engine.model.GeometryDoc;

import java.util.UUID;

public record FloorPlanDto(UUID id, UUID projectId, String name, String riskGroup,
                           Boolean sprinklered, Double escapeHeightMetres, GeometryDoc geometry) {
    public static FloorPlanDto from(FloorPlan fp) {
        return new FloorPlanDto(fp.getId(), fp.getProjectId(), fp.getName(), fp.getRiskGroup(),
                fp.getSprinklered(), fp.getEscapeHeightMetres(), fp.getGeometry());
    }
}

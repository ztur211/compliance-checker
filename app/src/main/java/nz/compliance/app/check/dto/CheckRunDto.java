package nz.compliance.app.check.dto;

import nz.compliance.app.check.CheckRun;
import nz.compliance.engine.check.CheckResult;

import java.util.UUID;

public record CheckRunDto(UUID id, UUID floorPlanId, String status, CheckResult result, String error) {
    public static CheckRunDto from(CheckRun r) {
        return new CheckRunDto(r.getId(), r.getFloorPlanId(), r.getStatus().name(), r.getResult(), r.getError());
    }
}

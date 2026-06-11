package nz.compliance.app.project.dto;

import nz.compliance.app.project.Project;

import java.util.UUID;

public record ProjectDto(UUID id, String name) {
    public static ProjectDto from(Project p) {
        return new ProjectDto(p.getId(), p.getName());
    }
}

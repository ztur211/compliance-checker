package nz.compliance.app.project;

import jakarta.validation.Valid;
import nz.compliance.app.project.dto.CreateProjectRequest;
import nz.compliance.app.project.dto.ProjectDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projects;

    public ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest req) {
        return ProjectDto.from(projects.save(new Project(req.name())));
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projects.findAll().stream().map(ProjectDto::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable UUID id) {
        return projects.findById(id).map(ProjectDto::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}

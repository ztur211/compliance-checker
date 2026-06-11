package nz.compliance.app.project;

import jakarta.validation.Valid;
import nz.compliance.app.project.dto.FloorPlanDto;
import nz.compliance.app.project.dto.SaveFloorPlanRequest;
import nz.compliance.engine.model.GeometryDoc;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FloorPlanController {

    private final FloorPlanRepository floorPlans;
    private final ProjectRepository projects;

    public FloorPlanController(FloorPlanRepository floorPlans, ProjectRepository projects) {
        this.floorPlans = floorPlans;
        this.projects = projects;
    }

    @PostMapping("/projects/{projectId}/floorplans")
    @ResponseStatus(HttpStatus.CREATED)
    public FloorPlanDto create(@PathVariable UUID projectId, @Valid @RequestBody CreateFloorPlanBody body) {
        if (!projects.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "project not found");
        }
        return FloorPlanDto.from(floorPlans.save(new FloorPlan(projectId, body.name())));
    }

    @GetMapping("/projects/{projectId}/floorplans")
    public List<FloorPlanDto> list(@PathVariable UUID projectId) {
        return floorPlans.findByProjectId(projectId).stream().map(FloorPlanDto::from).toList();
    }

    @GetMapping("/floorplans/{id}")
    public FloorPlanDto get(@PathVariable UUID id) {
        return floorPlans.findById(id).map(FloorPlanDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/floorplans/{id}")
    public FloorPlanDto save(@PathVariable UUID id, @Valid @RequestBody SaveFloorPlanRequest req) {
        FloorPlan fp = floorPlans.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GeometryDoc geometry = req.geometry() == null ? new GeometryDoc(1, List.of(), List.of()) : req.geometry();
        List<String> errors = geometry.validationErrors();
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", errors));
        }
        fp.setName(req.name());
        fp.setRiskGroup(req.riskGroup());
        fp.setSprinklered(req.sprinklered());
        fp.setEscapeHeightMetres(req.escapeHeightMetres());
        fp.setGeometry(geometry);
        return FloorPlanDto.from(floorPlans.save(fp));
    }

    public record CreateFloorPlanBody(@jakarta.validation.constraints.NotBlank String name) {
    }
}

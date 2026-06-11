package nz.compliance.app.check;

import nz.compliance.app.check.dto.CheckRunDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CheckController {

    private final CheckService checkService;
    private final CheckRunRepository runs;

    public CheckController(CheckService checkService, CheckRunRepository runs) {
        this.checkService = checkService;
        this.runs = runs;
    }

    @PostMapping("/floorplans/{id}/checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> start(@PathVariable UUID id) {
        return Map.of("runId", checkService.startCheck(id));
    }

    @GetMapping("/checks/{runId}")
    public CheckRunDto get(@PathVariable UUID runId) {
        return runs.findById(runId).map(CheckRunDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}

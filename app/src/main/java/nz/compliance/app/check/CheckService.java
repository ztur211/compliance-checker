package nz.compliance.app.check;

import nz.compliance.app.project.FloorPlanRepository;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CheckService {

    private final CheckRunRepository runs;
    private final FloorPlanRepository floorPlans;
    private final JobScheduler jobScheduler;
    private final CheckJob checkJob;

    public CheckService(CheckRunRepository runs, FloorPlanRepository floorPlans,
                        JobScheduler jobScheduler, CheckJob checkJob) {
        this.runs = runs;
        this.floorPlans = floorPlans;
        this.jobScheduler = jobScheduler;
        this.checkJob = checkJob;
    }

    public UUID startCheck(UUID floorPlanId) {
        if (!floorPlans.existsById(floorPlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "floor plan not found");
        }
        UUID runId = runs.save(new CheckRun(floorPlanId)).getId();
        jobScheduler.enqueue(() -> checkJob.run(runId));
        return runId;
    }
}

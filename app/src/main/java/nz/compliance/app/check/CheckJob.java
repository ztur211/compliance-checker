package nz.compliance.app.check;

import nz.compliance.app.project.FloorPlan;
import nz.compliance.app.project.FloorPlanRepository;
import nz.compliance.engine.check.CheckResult;
import nz.compliance.engine.check.ComplianceEngine;
import nz.compliance.engine.model.BuildingContext;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class CheckJob {

    private final CheckRunRepository runs;
    private final FloorPlanRepository floorPlans;
    private final DefaultNzEgressRuleSet ruleSet;
    private final ComplianceEngine engine = new ComplianceEngine();

    public CheckJob(CheckRunRepository runs, FloorPlanRepository floorPlans, DefaultNzEgressRuleSet ruleSet) {
        this.runs = runs;
        this.floorPlans = floorPlans;
        this.ruleSet = ruleSet;
    }

    @Job(name = "compliance-check")
    public void run(UUID runId) {
        CheckRun run = runs.findById(runId).orElseThrow();
        try {
            run.setStatus(CheckRun.Status.RUNNING);
            runs.save(run);

            FloorPlan fp = floorPlans.findById(run.getFloorPlanId()).orElseThrow();
            BuildingContext ctx = new BuildingContext(fp.getRiskGroup(),
                    Boolean.TRUE.equals(fp.getSprinklered()), fp.getEscapeHeightMetres());
            CheckResult result = engine.check(fp.getGeometry(), ctx, ruleSet.ruleSet());

            run.setResult(result);
            run.setStatus(CheckRun.Status.SUCCEEDED);
        } catch (Exception e) {
            run.setStatus(CheckRun.Status.FAILED);
            run.setError(e.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            runs.save(run);
        }
    }
}

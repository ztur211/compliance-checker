package nz.compliance.app.check;

import nz.compliance.app.project.FloorPlan;
import nz.compliance.app.project.FloorPlanRepository;
import nz.compliance.app.project.Project;
import nz.compliance.app.project.ProjectRepository;
import nz.compliance.app.support.PostgresIntegrationTest;
import nz.compliance.engine.check.CheckResult;
import nz.compliance.engine.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckFlowIT extends PostgresIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired FloorPlanRepository floorPlans;
    @Autowired CheckJob checkJob;
    @Autowired CheckRunRepository runs;

    @Test
    void runsCheckAndStoresViolations() {
        Project p = projects.save(new Project("P"));
        FloorPlan fp = new FloorPlan(p.getId(), "L1");
        fp.setRiskGroup("WB");
        fp.setSprinklered(true);
        // single space with one exit, occupant load fine, but only 1 escape route -> EXIT_COUNT violation
        fp.setGeometry(new GeometryDoc(1,
                List.of(new Space("s1", "Office", "WB",
                        List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)))),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true))));
        fp = floorPlans.save(fp);

        CheckRun run = runs.save(new CheckRun(fp.getId()));
        checkJob.run(run.getId());   // run synchronously (no scheduler in the test)

        CheckRun reloaded = runs.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CheckRun.Status.SUCCEEDED);
        CheckResult result = reloaded.getResult();
        assertThat(result.violations()).anyMatch(v -> v.ruleId().equals("escape-routes.min"));
    }
}

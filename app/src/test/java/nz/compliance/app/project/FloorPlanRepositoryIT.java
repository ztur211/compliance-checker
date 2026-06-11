package nz.compliance.app.project;

import nz.compliance.app.support.PostgresIntegrationTest;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FloorPlanRepositoryIT extends PostgresIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired FloorPlanRepository floorPlans;

    @Test
    void savesAndReloadsGeometryJson() {
        Project project = projects.save(new Project("Tower A"));

        GeometryDoc geometry = new GeometryDoc(1,
                List.of(new Space("s1", "Office", "WB",
                        List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)))),
                List.of(new Door("d1", "s1", null,
                        List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));

        FloorPlan fp = new FloorPlan(project.getId(), "Level 1");
        fp.setGeometry(geometry);
        fp.setRiskGroup("WB");
        fp.setSprinklered(true);
        UUID id = floorPlans.save(fp).getId();

        FloorPlan reloaded = floorPlans.findById(id).orElseThrow();
        assertThat(reloaded.getGeometry().spaces()).hasSize(1);
        assertThat(reloaded.getGeometry().doors().get(0).exit()).isTrue();
        assertThat(reloaded.getRiskGroup()).isEqualTo("WB");
    }
}

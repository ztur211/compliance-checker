package nz.compliance.app.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FloorPlanRepository extends JpaRepository<FloorPlan, UUID> {
    List<FloorPlan> findByProjectId(UUID projectId);
}

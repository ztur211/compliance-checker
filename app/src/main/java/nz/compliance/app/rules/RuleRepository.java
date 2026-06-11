package nz.compliance.app.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {
    List<RuleEntity> findByRuleSetId(UUID ruleSetId);
    List<RuleEntity> findByRuleSetIdAndStatus(UUID ruleSetId, RuleEntity.RuleStatus status);
    List<RuleEntity> findByStatus(RuleEntity.RuleStatus status);
}

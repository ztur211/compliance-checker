package nz.compliance.app.rules;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RuleReviewService {

    private final RuleRepository rules;
    private final RuleSetRepository ruleSets;

    public RuleReviewService(RuleRepository rules, RuleSetRepository ruleSets) {
        this.rules = rules;
        this.ruleSets = ruleSets;
    }

    public void approve(UUID ruleId) { setStatus(ruleId, RuleEntity.RuleStatus.APPROVED); }
    public void reject(UUID ruleId) { setStatus(ruleId, RuleEntity.RuleStatus.REJECTED); }

    public void edit(UUID ruleId, String parameter, String comparator, double threshold) {
        RuleEntity r = rules.findById(ruleId).orElseThrow();
        r.setParameter(parameter);
        r.setComparator(comparator);
        r.setThreshold(threshold);
        rules.save(r);
    }

    /** Marks a rule set active (deactivating others). Only approved rules will be used. */
    public void activate(UUID ruleSetId) {
        ruleSets.findAll().forEach(rs -> { rs.setActive(rs.getId().equals(ruleSetId)); ruleSets.save(rs); });
    }

    private void setStatus(UUID ruleId, RuleEntity.RuleStatus status) {
        RuleEntity r = rules.findById(ruleId).orElseThrow();
        r.setStatus(status);
        rules.save(r);
    }
}

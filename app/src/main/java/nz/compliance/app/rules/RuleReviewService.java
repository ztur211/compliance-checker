package nz.compliance.app.rules;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@Service
public class RuleReviewService {

    private final RuleRepository rules;
    private final RuleSetRepository ruleSets;
    private final CandidateValidator validator;

    public RuleReviewService(RuleRepository rules, RuleSetRepository ruleSets, CandidateValidator validator) {
        this.rules = rules;
        this.ruleSets = ruleSets;
        this.validator = validator;
    }

    public void approve(UUID ruleId) { setStatus(ruleId, RuleEntity.RuleStatus.APPROVED); }
    public void reject(UUID ruleId) { setStatus(ruleId, RuleEntity.RuleStatus.REJECTED); }

    /**
     * Re-applies the deterministic guardrail on the human-edit path: an edit cannot introduce a
     * parameter/comparator the engine can't map, which would otherwise fail EVERY check run once the
     * rule is approved and its set activated. Rejects with 422.
     */
    public void edit(UUID ruleId, String parameter, String comparator, double threshold) {
        RuleEntity r = rule(ruleId);
        String quote = (r.getSourceQuote() == null || r.getSourceQuote().isBlank()) ? "(edited)" : r.getSourceQuote();
        RuleCandidate edited = new RuleCandidate(r.getCitation(), r.getTitle(), parameter, comparator,
                threshold, Set.of(), quote, r.getConfidence() == null ? 1.0 : r.getConfidence());
        validator.validate(edited).ifPresent(err -> {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, err);
        });
        r.setParameter(parameter);
        r.setComparator(comparator);
        r.setThreshold(threshold);
        rules.save(r);
    }

    /** Marks a rule set active (deactivating others). Only approved rules will be used. */
    public void activate(UUID ruleSetId) {
        if (!ruleSets.existsById(ruleSetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "rule set not found");
        }
        ruleSets.findAll().forEach(rs -> {
            boolean shouldBeActive = rs.getId().equals(ruleSetId);
            if (rs.isActive() != shouldBeActive) {
                rs.setActive(shouldBeActive);
                ruleSets.save(rs);
            }
        });
    }

    private void setStatus(UUID ruleId, RuleEntity.RuleStatus status) {
        RuleEntity r = rule(ruleId);
        r.setStatus(status);
        rules.save(r);
    }

    private RuleEntity rule(UUID ruleId) {
        return rules.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rule not found"));
    }
}

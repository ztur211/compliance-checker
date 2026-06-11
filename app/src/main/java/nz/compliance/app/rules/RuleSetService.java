package nz.compliance.app.rules;

import nz.compliance.app.check.DefaultNzEgressRuleSet;
import nz.compliance.engine.rules.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RuleSetService {

    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;
    private final DefaultNzEgressRuleSet fallback;

    public RuleSetService(RuleSetRepository ruleSets, RuleRepository rules, DefaultNzEgressRuleSet fallback) {
        this.ruleSets = ruleSets;
        this.rules = rules;
        this.fallback = fallback;
    }

    public RuleSet activeRuleSet() {
        return ruleSets.findFirstByActiveTrueOrderByCreatedAtDesc()
                .map(this::toEngineRuleSet)
                .filter(rs -> !rs.rules().isEmpty())
                .orElseGet(fallback::ruleSet);
    }

    private RuleSet toEngineRuleSet(RuleSetEntity rs) {
        List<Rule> engineRules = rules.findByRuleSetIdAndStatus(rs.getId(), RuleEntity.RuleStatus.APPROVED).stream()
                .map(this::toEngineRuleOrNull)
                .filter(Objects::nonNull)
                .toList();
        return new RuleSet(rs.getName(), rs.getVersion(), engineRules);
    }

    private Rule toEngineRuleOrNull(RuleEntity e) {
        try {
            Set<String> groups = e.getRiskGroups() == null || e.getRiskGroups().isBlank()
                    ? Set.of() : Set.copyOf(Arrays.asList(e.getRiskGroups().split(",")));
            return new Rule(e.getId().toString(), e.getCitation(), e.getTitle(),
                    ParameterKey.valueOf(e.getParameter()), Comparator.valueOf(e.getComparator()),
                    e.getThreshold(), Severity.valueOf(e.getSeverity()), groups);
        } catch (IllegalArgumentException ex) {
            // An unmappable enum (shouldn't happen — extraction and edit both validate) must not poison
            // the whole active rule set and fail every check. Skip the bad row instead.
            return null;
        }
    }
}

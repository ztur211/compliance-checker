package nz.compliance.engine.rules;

import nz.compliance.engine.model.BuildingContext;

import java.util.Set;

/**
 * One machine-checkable constraint. {@code riskGroups} empty == applies to all.
 * v1 threshold is a single value; context-keyed threshold tables are a later enhancement.
 */
public record Rule(String id, String citation, String title, ParameterKey parameter,
                   Comparator comparator, double threshold, Severity severity, Set<String> riskGroups) {

    public Rule {
        riskGroups = riskGroups == null ? Set.of() : Set.copyOf(riskGroups);
    }

    public boolean appliesTo(BuildingContext ctx) {
        return riskGroups.isEmpty() || (ctx.riskGroup() != null && riskGroups.contains(ctx.riskGroup()));
    }
}

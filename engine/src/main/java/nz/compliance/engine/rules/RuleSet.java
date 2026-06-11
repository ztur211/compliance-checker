package nz.compliance.engine.rules;

import nz.compliance.engine.model.BuildingContext;

import java.util.List;

public record RuleSet(String name, String version, List<Rule> rules) {

    public RuleSet {
        rules = List.copyOf(rules);
    }

    /** Filters to rules applicable to the given context. */
    public List<Rule> resolve(BuildingContext ctx) {
        return rules.stream().filter(r -> r.appliesTo(ctx)).toList();
    }
}

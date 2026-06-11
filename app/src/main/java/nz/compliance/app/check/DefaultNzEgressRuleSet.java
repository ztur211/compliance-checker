package nz.compliance.app.check;

import nz.compliance.engine.rules.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class DefaultNzEgressRuleSet {

    public RuleSet ruleSet() {
        return new RuleSet("NZBC C/AS2 — Means of Escape (illustrative)", "v1", List.of(
                new Rule("openpath.commercial", "C/AS2 open path (illustrative)", "Max open path",
                        ParameterKey.OPEN_PATH_LENGTH, Comparator.LTE, 18.0, Severity.ERROR, Set.of()),
                new Rule("escape-routes.min", "C/AS2 escape routes (illustrative)", "Min escape routes",
                        ParameterKey.EXIT_COUNT, Comparator.GTE, 2.0, Severity.ERROR, Set.of())));
    }
}

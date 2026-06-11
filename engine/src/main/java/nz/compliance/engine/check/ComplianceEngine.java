package nz.compliance.engine.check;

import nz.compliance.engine.egress.EgressAnalyzer;
import nz.compliance.engine.egress.EgressResult;
import nz.compliance.engine.egress.SpaceEgress;
import nz.compliance.engine.facts.FactsComputer;
import nz.compliance.engine.facts.PlanFacts;
import nz.compliance.engine.facts.SpaceFacts;
import nz.compliance.engine.model.BuildingContext;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Space;
import nz.compliance.engine.rules.ParameterKey;
import nz.compliance.engine.rules.Rule;
import nz.compliance.engine.rules.RuleSet;
import nz.compliance.engine.rules.Scope;
import nz.compliance.engine.rules.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The deterministic compliance check. Pure: no Spring, DB, web, or LLM. */
public final class ComplianceEngine {

    public CheckResult check(GeometryDoc doc, BuildingContext ctx, RuleSet ruleSet) {
        PlanFacts facts = FactsComputer.compute(doc);
        EgressResult egress = EgressAnalyzer.analyze(doc);

        if (facts.exitDoorCount() == 0) {
            return CheckResult.blocked(
                    "No exits defined — add at least one exit door to check means of escape.");
        }

        Map<String, String> names = new HashMap<>();
        for (Space s : doc.spaces()) {
            names.put(s.id(), s.name());
        }

        Map<String, Boolean> valid = new HashMap<>();
        for (SpaceFacts sf : facts.spaces()) {
            valid.put(sf.spaceId(), sf.valid());
        }
        Set<String> spacesWithDoor = new HashSet<>();
        for (Door d : doc.doors()) {
            spacesWithDoor.add(d.fromSpaceId());
            if (d.toSpaceId() != null) {
                spacesWithDoor.add(d.toSpaceId());
            }
        }

        List<Violation> violations = new ArrayList<>();
        List<RuleOutcome> passed = new ArrayList<>();
        List<RuleOutcome> notEvaluated = new ArrayList<>();

        // Spec §11: a space that cannot reach an exit is only a "no means of escape"
        // violation when it is geometrically valid AND actually has a door. A degenerate
        // polygon or a door-less (unconnected) room is an incomplete model -> not evaluated.
        for (SpaceEgress se : egress.unreachable()) {
            String id = se.spaceId();
            if (!valid.getOrDefault(id, false)) {
                notEvaluated.add(new RuleOutcome("structural.no-egress", null, id, null,
                        OutcomeStatus.NOT_EVALUATED, "degenerate geometry — space not evaluated"));
            } else if (!spacesWithDoor.contains(id)) {
                notEvaluated.add(new RuleOutcome("structural.no-egress", null, id, null,
                        OutcomeStatus.NOT_EVALUATED, "no door — incomplete model, space not evaluated"));
            } else {
                violations.add(new Violation("structural.no-egress", "C/AS2 means of escape", Severity.ERROR,
                        "No means of escape: " + label(names, id) + " cannot reach any exit.",
                        null, null, null, id, List.of()));
            }
        }

        for (Rule rule : ruleSet.resolve(ctx)) {
            if (rule.parameter().scope() == Scope.PER_SPACE) {
                for (Space s : doc.spaces()) {
                    evaluate(rule, s.id(), facts, egress, names, violations, passed, notEvaluated);
                }
            } else {
                evaluate(rule, null, facts, egress, names, violations, passed, notEvaluated);
            }
        }
        return new CheckResult(violations, passed, notEvaluated, false, null);
    }

    private void evaluate(Rule rule, String spaceId, PlanFacts facts, EgressResult egress,
                          Map<String, String> names, List<Violation> violations,
                          List<RuleOutcome> passed, List<RuleOutcome> notEvaluated) {
        Optional<Double> value = ParameterRegistry.value(rule.parameter(), facts, egress, spaceId);
        if (value.isEmpty()) {
            notEvaluated.add(new RuleOutcome(rule.id(), rule.parameter(), spaceId, null,
                    OutcomeStatus.NOT_EVALUATED, "no value (e.g. unreachable or degenerate space)"));
            return;
        }
        double v = value.get();
        if (rule.comparator().test(v, rule.threshold())) {
            passed.add(new RuleOutcome(rule.id(), rule.parameter(), spaceId, v, OutcomeStatus.PASS, "ok"));
            return;
        }
        List<String> path = (rule.parameter() == ParameterKey.OPEN_PATH_LENGTH && spaceId != null)
                ? egress.bySpace().get(spaceId).pathNodeIds() : List.of();
        violations.add(new Violation(rule.id(), rule.citation(), rule.severity(),
                message(rule, spaceId, names, v), rule.parameter(), v, rule.threshold(), spaceId, path));
    }

    private String message(Rule rule, String spaceId, Map<String, String> names, double v) {
        String where = spaceId == null ? "Plan" : label(names, spaceId);
        return switch (rule.parameter()) {
            case OPEN_PATH_LENGTH -> "Open path from " + where + " is " + round(v) + " m; max "
                    + round(rule.threshold()) + " m (" + rule.citation() + ").";
            case EXIT_COUNT -> where + " has " + (int) v + " escape route(s); minimum "
                    + (int) rule.threshold() + " (" + rule.citation() + ").";
            case EXIT_WIDTH -> where + " has " + round(v) + " mm exit width; minimum "
                    + round(rule.threshold()) + " mm (" + rule.citation() + ").";
            case OCCUPANT_LOAD -> where + " occupant load " + round(v) + "; limit "
                    + round(rule.threshold()) + " (" + rule.citation() + ").";
        };
    }

    private static String round(double d) {
        return String.valueOf(Math.round(d * 10.0) / 10.0);
    }

    private static String label(Map<String, String> names, String id) {
        String n = names.get(id);
        return (n == null ? id : n) + " (" + id + ")";
    }
}

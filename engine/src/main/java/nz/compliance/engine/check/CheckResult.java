package nz.compliance.engine.check;

import java.util.List;

public record CheckResult(List<Violation> violations, List<RuleOutcome> passed,
                          List<RuleOutcome> notEvaluated, boolean blocked, String blockMessage) {

    public CheckResult {
        violations = List.copyOf(violations);
        passed = List.copyOf(passed);
        notEvaluated = List.copyOf(notEvaluated);
    }

    public static CheckResult blocked(String message) {
        return new CheckResult(List.of(), List.of(), List.of(), true, message);
    }
}

package nz.compliance.engine.check;

import nz.compliance.engine.rules.ParameterKey;

public record RuleOutcome(String ruleId, ParameterKey parameter, String spaceId,
                          Double computedValue, OutcomeStatus status, String reason) {
}

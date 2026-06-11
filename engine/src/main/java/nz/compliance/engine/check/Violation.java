package nz.compliance.engine.check;

import nz.compliance.engine.rules.ParameterKey;
import nz.compliance.engine.rules.Severity;

import java.util.List;

/** A located rule failure. spaceId/pathNodeIds drive highlighting; may be null/empty for plan-level. */
public record Violation(String ruleId, String citation, Severity severity, String message,
                        ParameterKey parameter, Double computedValue, Double threshold,
                        String spaceId, List<String> pathNodeIds) {
    public Violation {
        pathNodeIds = pathNodeIds == null ? List.of() : List.copyOf(pathNodeIds);
    }
}

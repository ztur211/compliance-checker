package nz.compliance.app.rules;

import java.util.Set;

/** An LLM-proposed rule, with provenance. parameter/comparator are strings until validated. */
public record RuleCandidate(String citation, String title, String parameter, String comparator,
                            double threshold, Set<String> riskGroups, String sourceQuote, double confidence) {
    public RuleCandidate {
        riskGroups = riskGroups == null ? Set.of() : Set.copyOf(riskGroups);
    }
}

package nz.compliance.app.rules;

import java.util.List;

/** Wrapper the LLM populates (LangChain4j maps the JSON to this POJO). */
public record ExtractionOutput(List<RuleCandidate> rules) {
    public ExtractionOutput {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}

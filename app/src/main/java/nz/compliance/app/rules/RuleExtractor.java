package nz.compliance.app.rules;

import java.util.List;

/** Seam over the LLM so tests can stub it (no live API in CI). */
public interface RuleExtractor {
    List<RuleCandidate> extract(String clauseText);
}

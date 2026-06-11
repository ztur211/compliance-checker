package nz.compliance.app.rules;

import nz.compliance.engine.rules.Comparator;
import nz.compliance.engine.rules.ParameterKey;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/** Deterministic guardrail around the LLM output. Returns an error message, or empty if valid. */
@Component
public class CandidateValidator {

    public Optional<String> validate(RuleCandidate c) {
        if (c.parameter() == null || Arrays.stream(ParameterKey.values()).noneMatch(p -> p.name().equals(c.parameter()))) {
            return Optional.of("unknown parameter: " + c.parameter());
        }
        if (c.comparator() == null || Arrays.stream(Comparator.values()).noneMatch(k -> k.name().equals(c.comparator()))) {
            return Optional.of("unknown comparator: " + c.comparator());
        }
        if (Double.isNaN(c.threshold()) || Double.isInfinite(c.threshold())) {
            return Optional.of("threshold is not a finite number");
        }
        if (c.sourceQuote() == null || c.sourceQuote().isBlank()) {
            return Optional.of("missing sourceQuote (no provenance)");
        }
        return Optional.empty();
    }
}

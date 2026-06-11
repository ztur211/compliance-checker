package nz.compliance.app.rules;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateValidatorTest {

    private final CandidateValidator validator = new CandidateValidator();

    @Test
    void acceptsAWellFormedCandidate() {
        RuleCandidate c = new RuleCandidate("C/AS2 3.x", "Open path",
                "OPEN_PATH_LENGTH", "LTE", 18.0, Set.of("WB"), "open paths shall not exceed...", 0.9);
        assertThat(validator.validate(c)).isEmpty();
    }

    @Test
    void rejectsUnknownParameter() {
        RuleCandidate c = new RuleCandidate("x", "t", "SPRINKLER_PRESSURE", "LTE", 1, Set.of(), "q", 0.5);
        assertThat(validator.validate(c)).get().asString().contains("parameter");
    }

    @Test
    void rejectsUnknownComparatorAndBadThreshold() {
        RuleCandidate c = new RuleCandidate("x", "t", "EXIT_COUNT", "BETWEEN", Double.NaN, Set.of(), "q", 0.5);
        assertThat(validator.validate(c)).isPresent();
    }
}

package nz.compliance.app.rules;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Precision/recall of the LLM extractor against ~10 hand-codified provisions.
 * Run manually: ./mvnw -pl app test -Dtest=GoldSetEvalTest -Dgroups=eval
 * Requires ANTHROPIC_API_KEY. This is a METRIC, not a pass/fail CI gate.
 */
@Tag("eval")
class GoldSetEvalTest {

    record Gold(String clause, String expectedParameter, double expectedThreshold) {}

    // Replace clause text + expected values with authoritative C/AS2 provisions during domain validation.
    private static final List<Gold> GOLD = List.of(
            new Gold("3.1 Open paths shall not exceed 18 m.", "OPEN_PATH_LENGTH", 18.0),
            new Gold("Dead-end open paths shall not exceed 6 m.", "DEAD_END_LENGTH", 6.0)
            // ... extend to ~10
    );

    @Test
    void reportPrecisionRecall() {
        RuleExtractor extractor = new LangChain4jRuleExtractor(System.getenv("ANTHROPIC_API_KEY"));
        CandidateValidator validator = new CandidateValidator();

        int truePositives = 0, predicted = 0;
        for (Gold g : GOLD) {
            List<RuleCandidate> got = extractor.extract(g.clause()).stream()
                    .filter(c -> validator.validate(c).isEmpty()).toList();
            predicted += got.size();
            boolean hit = got.stream().anyMatch(c -> c.parameter().equals(g.expectedParameter())
                    && Math.abs(c.threshold() - g.expectedThreshold()) < 0.01);
            if (hit) truePositives++;
        }
        double recall = (double) truePositives / GOLD.size();
        double precision = predicted == 0 ? 0 : (double) truePositives / predicted;
        System.out.printf("Gold-set eval: precision=%.2f recall=%.2f (tp=%d predicted=%d gold=%d)%n",
                precision, recall, truePositives, predicted, GOLD.size());
        assertThat(recall).isGreaterThanOrEqualTo(0.0); // metric only; never fails CI
    }
}

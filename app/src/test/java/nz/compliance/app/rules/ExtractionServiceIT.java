package nz.compliance.app.rules;

import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import(ExtractionServiceIT.StubConfig.class)
class ExtractionServiceIT extends PostgresIntegrationTest {

    @TestConfiguration
    static class StubConfig {
        @Bean RuleExtractor ruleExtractor() {
            return clause -> List.of(
                    new RuleCandidate("C/AS2 3.1", "Open path", "OPEN_PATH_LENGTH", "LTE", 18.0,
                            Set.of("WB"), "open paths shall not exceed 18 m", 0.92),
                    new RuleCandidate("C/AS2 bad", "junk", "NOPE", "LTE", 1.0, Set.of(), "x", 0.1)); // invalid -> dropped
        }
    }

    @Autowired ExtractionService extraction;
    @Autowired RuleRepository rules;

    @Test
    void storesOnlyValidCandidatesAsDraft() {
        UUID ruleSetId = extraction.extractInto("NZBC C/AS2", "v1", List.of("3.1 Open paths ..."));
        assertThat(rules.findByRuleSetIdAndStatus(ruleSetId, RuleEntity.RuleStatus.DRAFT))
                .hasSize(1)
                .allMatch(r -> r.getParameter().equals("OPEN_PATH_LENGTH"));
    }
}

package nz.compliance.app.rules;

import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// activate() flips a GLOBAL active flag in the shared (singleton-container) test DB.
// Roll back per test so an activated rule set never leaks into other ITs (e.g. CheckFlowIT,
// which expects the fallback DefaultNzEgressRuleSet).
@Transactional
class RuleSetServiceIT extends PostgresIntegrationTest {

    @Autowired RuleSetRepository ruleSets;
    @Autowired RuleRepository rules;
    @Autowired RuleReviewService review;
    @Autowired RuleSetService service;

    @Test
    void activeRuleSetContainsOnlyApprovedRules() {
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity("NZBC C/AS2", "v1"));
        RuleEntity approved = rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "EXIT_COUNT", "GTE", 2, Set.of(), "q", 0.9)));
        rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "OPEN_PATH_LENGTH", "LTE", 18, Set.of(), "q", 0.9))); // stays DRAFT

        review.approve(approved.getId());
        review.activate(rs.getId());

        assertThat(service.activeRuleSet().rules()).extracting(r -> r.parameter().name())
                .containsExactly("EXIT_COUNT");
    }

    @Test
    void fallsBackToDefaultWhenNoActiveApprovedRules() {
        assertThat(service.activeRuleSet().rules()).isNotEmpty(); // DefaultNzEgressRuleSet
    }

    @Test
    void editRejectsAnInvalidParameter() {
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity("NZBC C/AS2", "v1"));
        RuleEntity r = rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "EXIT_COUNT", "GTE", 2, Set.of(), "q", 0.9)));

        // a human edit must not be able to introduce a non-engine parameter
        assertThatThrownBy(() -> review.edit(r.getId(), "DEAD_END_LENGTH", "LTE", 6.0))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void activeRuleSetSkipsUnmappableApprovedRows() {
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity("NZBC C/AS2", "v1"));
        RuleEntity good = rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "EXIT_COUNT", "GTE", 2, Set.of(), "q", 0.9)));
        // an unmappable enum value persisted directly (bypassing validation) must not poison the whole set
        RuleEntity bad = rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "NOPE", "GTE", 2, Set.of(), "q", 0.9)));

        review.approve(good.getId());
        review.approve(bad.getId());
        review.activate(rs.getId());

        assertThat(service.activeRuleSet().rules()).extracting(r -> r.parameter().name())
                .containsExactly("EXIT_COUNT"); // bad row skipped, not fatal
    }
}

package nz.compliance.app.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRuleSeederTest {

    // repos are unused by loadCandidates(); null is safe for this pure-loader test
    private final DemoRuleSeeder seeder = new DemoRuleSeeder(null, null);

    @Test
    void loadsValidDemoCandidatesFromClasspath() {
        var candidates = seeder.loadCandidates();
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allSatisfy(c -> {
            assertThat(c.title()).isNotBlank();
            assertThat(c.citation()).isNotBlank();
            assertThat(c.parameter()).isNotBlank();
            assertThat(c.comparator()).isNotBlank();
            assertThat(c.threshold()).isGreaterThan(0);
        });
    }
}

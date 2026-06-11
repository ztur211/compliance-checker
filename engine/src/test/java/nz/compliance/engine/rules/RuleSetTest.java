package nz.compliance.engine.rules;

import nz.compliance.engine.model.BuildingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetTest {

    @Test
    void resolve_keepsOnlyApplicableRiskGroups() {
        Rule wbOnly = new Rule("r1", "c", "t", ParameterKey.EXIT_COUNT, Comparator.GTE, 2, Severity.ERROR, Set.of("WB"));
        Rule all = new Rule("r2", "c", "t", ParameterKey.OPEN_PATH_LENGTH, Comparator.LTE, 18, Severity.ERROR, Set.of());
        RuleSet rs = new RuleSet("nz", "v1", List.of(wbOnly, all));

        assertThat(rs.resolve(new BuildingContext("WB", true, 3.0))).extracting(Rule::id).containsExactly("r1", "r2");
        assertThat(rs.resolve(new BuildingContext("CA", true, 3.0))).extracting(Rule::id).containsExactly("r2");
    }

    @Test
    void comparators_behaveAsExpected() {
        assertThat(Comparator.LTE.test(5, 10)).isTrue();
        assertThat(Comparator.GTE.test(1, 2)).isFalse();
    }
}

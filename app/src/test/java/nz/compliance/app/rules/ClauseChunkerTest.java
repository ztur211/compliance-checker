package nz.compliance.app.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClauseChunkerTest {

    @Test
    void splitsOnClauseHeadings() {
        String text = """
            3.1 Open paths
            Open paths shall not exceed the lengths in Table 3.

            3.2 Dead ends
            Dead-end open paths shall not exceed 6 m.
            """;
        var clauses = new ClauseChunker().chunk(text);
        assertThat(clauses).hasSize(2);
        assertThat(clauses.get(0).citation()).isEqualTo("3.1");
        assertThat(clauses.get(1).text()).contains("Dead-end");
    }
}

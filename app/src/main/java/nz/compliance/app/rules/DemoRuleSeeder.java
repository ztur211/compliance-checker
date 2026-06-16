package nz.compliance.app.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Seeds DRAFT rule candidates on boot (under the "demo" profile) so the "Rule review" tab has real,
 * AI-codified C/AS2 cards to demonstrate the human-in-the-loop approval flow. Idempotent: skips if
 * the demo rule set already exists. The set is left INACTIVE so checks keep using the engine's
 * fallback rule set unchanged. Candidates come from classpath {@code demo/rules.json}.
 */
@Component
@Profile("demo")
public class DemoRuleSeeder implements ApplicationRunner {

    static final String DEMO_SET_NAME = "NZBC C/AS2 — Means of Escape (AI-codified, demo)";
    static final String DEMO_SET_VERSION = "demo-1";
    static final String FIXTURE = "demo/rules.json";

    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;
    private final ObjectMapper mapper = new ObjectMapper();

    public DemoRuleSeeder(RuleSetRepository ruleSets, RuleRepository rules) {
        this.ruleSets = ruleSets;
        this.rules = rules;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = ruleSets.findAll().stream().anyMatch(
                rs -> DEMO_SET_NAME.equals(rs.getName()) && DEMO_SET_VERSION.equals(rs.getVersion()));
        if (exists) {
            return;
        }
        List<RuleCandidate> candidates = loadCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity(DEMO_SET_NAME, DEMO_SET_VERSION));
        for (RuleCandidate c : candidates) {
            rules.save(new RuleEntity(rs.getId(), c));   // RuleEntity defaults status to DRAFT
        }
    }

    /** Load demo candidates from classpath; empty list if the fixture is missing/unreadable. */
    List<RuleCandidate> loadCandidates() {
        ClassPathResource res = new ClassPathResource(FIXTURE);
        if (!res.exists()) {
            return List.of();
        }
        try (InputStream in = res.getInputStream()) {
            return mapper.readValue(in, new TypeReference<List<RuleCandidate>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}

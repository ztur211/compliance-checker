package nz.compliance.app.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.compliance.app.imports.ClaudeVisionPlanExtractor;
import nz.compliance.app.imports.PlanExtraction;
import nz.compliance.app.imports.PlanImageRenderer;
import nz.compliance.app.rules.LangChain4jRuleExtractor;
import nz.compliance.app.rules.RuleCandidate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time, owner-run capture of REAL Claude outputs into the committed demo fixtures the "demo"
 * profile replays. Needs ANTHROPIC_API_KEY; {@code @Tag("capture")} keeps it out of CI. The tests
 * instantiate the extractors directly (no Spring context, no DB).
 *
 * Run:  ANTHROPIC_API_KEY=sk-ant-... ./mvnw -pl app test -Dtest=CaptureDemoFixturesTest -DexcludedGroups=
 */
@Tag("capture")
class CaptureDemoFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path DEMO_DIR = Path.of("src/main/resources/demo");
    // Paths are relative to the app/ module dir, where Maven surefire runs (same convention as VisionPlanExtractorEvalTest).
    private static final String DEMO_IMAGE = "src/test/resources/import-gold/wealthy-home-sample.jpg";

    // Representative C/AS2 clause texts. Replace with exact wording you are licensed to use.
    private static final List<String> CLAUSES = List.of(
            "The maximum length of an open path shall not exceed 18 m.",
            "Every firecell shall be provided with not less than two escape routes where the occupant load exceeds 50.",
            "The minimum clear width of any escape route shall be 850 mm.");

    @Test
    void captureImportExtraction() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping capture");
        PlanExtraction ex = new ClaudeVisionPlanExtractor(key)
                .extract(new PlanImageRenderer().render(Files.readAllBytes(Path.of(DEMO_IMAGE))));
        Files.createDirectories(DEMO_DIR);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(DEMO_DIR.resolve("extraction.json").toFile(), ex);
        System.out.println("[capture] demo/extraction.json: rooms=" + ex.rooms().size()
                + " doors=" + ex.doors().size() + " scale=" + ex.scaleGuess());
    }

    @Test
    void captureRuleCandidates() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping capture");
        LangChain4jRuleExtractor extractor = new LangChain4jRuleExtractor(key);
        List<RuleCandidate> all = new ArrayList<>();
        for (String clause : CLAUSES) {
            all.addAll(extractor.extract(clause));
        }
        Assumptions.assumeTrue(!all.isEmpty(), "extractor returned no candidates");
        Files.createDirectories(DEMO_DIR);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(DEMO_DIR.resolve("rules.json").toFile(), all);
        System.out.println("[capture] demo/rules.json: " + all.size() + " candidates");
    }
}

package nz.compliance.app.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live accuracy eval — needs ANTHROPIC_API_KEY and {@code -Dspring.profiles.active=claude}; the
 * {@code @Tag("eval")} keeps it out of CI (excludedGroups). It runs the real vision extractor over
 * every image in import-gold/, scores each against its sibling {@code <image>.gold.json} with
 * {@link ExtractionScorer}, prints a per-image scorecard, and fails if any scored image dips below a
 * floor. Images without a gold are reported (counts only), not scored.
 */
@Tag("eval")
class VisionPlanExtractorEvalTest {

    // Floors catch catastrophic regression, NOT quality targets — tune once a real baseline run exists.
    private static final double ROOM_RECALL_FLOOR = 0.4;

    @Test
    void scoresExtractionAgainstGoldImages() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping eval");
        Path dir = Path.of("src/test/resources/import-gold");
        Assumptions.assumeTrue(Files.isDirectory(dir), "no import-gold images; skipping eval");

        ClaudeVisionPlanExtractor extractor = new ClaudeVisionPlanExtractor(key);
        PlanImageRenderer renderer = new PlanImageRenderer();
        ExtractionScorer scorer = new ExtractionScorer();
        ObjectMapper mapper = new ObjectMapper();

        List<String> failures = new ArrayList<>();
        int scored = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path img : files.filter(p -> p.toString().matches(".*\\.(png|jpg|jpeg|pdf)")).sorted().toList()) {
                PlanExtraction ex = extractor.extract(renderer.render(Files.readAllBytes(img)));
                Path goldPath = dir.resolve(baseName(img) + ".gold.json");
                if (!Files.exists(goldPath)) {
                    System.out.printf("[eval] %-32s rooms=%d doors=%d scale=%s (no gold; not scored)%n",
                            img.getFileName(), ex.rooms().size(), ex.doors().size(),
                            ex.scaleGuess() == null ? "null" : ex.scaleGuess().metresPerPixel());
                    continue;
                }
                GoldPlan gold = mapper.readValue(Files.readAllBytes(goldPath), GoldPlan.class);
                ExtractionScorer.ScoreReport r = scorer.score(ex, gold);
                scored++;
                System.out.printf("[eval] %-32s %s%n", img.getFileName(), r.summary());

                if (!gold.rooms().isEmpty() && r.roomRecall() < ROOM_RECALL_FLOOR) {
                    failures.add(img.getFileName() + ": room recall "
                            + String.format("%.2f", r.roomRecall()) + " < floor " + ROOM_RECALL_FLOOR);
                }
                if (r.scaleScored() && !r.scaleOk()) {
                    failures.add(img.getFileName() + ": scale wrong — " + r.summary());
                }
            }
        }
        Assumptions.assumeTrue(scored > 0, "no *.gold.json fixtures; nothing to score");
        assertThat(failures).as("eval accuracy floors").isEmpty();
    }

    private static String baseName(Path img) {
        String n = img.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }
}

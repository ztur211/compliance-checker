package nz.compliance.app.imports;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Tag("eval")
class VisionPlanExtractorEvalTest {

    @Test
    void reportsExtractionMetricsOnGoldImages() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping eval");
        Path dir = Path.of("src/test/resources/import-gold");
        Assumptions.assumeTrue(Files.isDirectory(dir), "no import-gold images; skipping eval");

        ClaudeVisionPlanExtractor extractor = new ClaudeVisionPlanExtractor(key);
        PlanImageRenderer renderer = new PlanImageRenderer();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path img : files.filter(p -> p.toString().matches(".*\\.(png|jpg|jpeg|pdf)")).toList()) {
                RenderedImage rendered = renderer.render(Files.readAllBytes(img));
                PlanExtraction ex = extractor.extract(rendered);
                System.out.printf("[eval] %s -> rooms=%d doors=%d scale=%s%n",
                        img.getFileName(), ex.rooms().size(), ex.doors().size(),
                        ex.scaleGuess() == null ? "null" : ex.scaleGuess().metresPerPixel());
            }
        }
    }
}

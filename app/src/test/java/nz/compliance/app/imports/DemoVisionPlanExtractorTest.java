package nz.compliance.app.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoVisionPlanExtractorTest {

    private final DemoVisionPlanExtractor extractor = new DemoVisionPlanExtractor();
    private final RenderedImage image = new RenderedImage(new byte[0], 1600, 1000);

    @Test
    void fallbackPlanHasThreeRoomsAndExactlyOneExitWithinBounds() {
        PlanExtraction ex = extractor.fallback(image);
        assertThat(ex.rooms()).hasSize(3);
        long exits = ex.doors().stream().filter(ExtractedDoor::exitGuess).count();
        assertThat(exits).isEqualTo(1);                 // single exit -> guaranteed escape-routes violation
        assertThat(ex.scaleGuess()).isNotNull();
        assertThat(ex.scaleGuess().metresPerPixel()).isGreaterThan(0);
        ex.rooms().forEach(r -> r.polygonPx().forEach(p -> {
            assertThat(p.x()).isBetween(0.0, 1600.0);
            assertThat(p.y()).isBetween(0.0, 1000.0);
        }));
    }

    @Test
    void extractUsesFallbackWhenNoCaptureFixtureOnClasspath() {
        PlanExtraction ex = extractor.extract(image);   // no demo/extraction.json in test classpath
        assertThat(ex.rooms()).hasSize(3);
    }
}

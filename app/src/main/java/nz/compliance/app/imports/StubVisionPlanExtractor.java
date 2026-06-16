package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default extractor (active unless the "claude" profile is on). Produces a single room covering the
 * image and nothing else, so the user always lands on the backdrop with something to calibrate and
 * correct — the graceful-degradation floor when no vision model is configured.
 */
@Component
@Profile("!claude & !demo")
public class StubVisionPlanExtractor implements VisionPlanExtractor {

    @Override
    public PlanExtraction extract(RenderedImage image) {
        double w = image.widthPx();
        double h = image.heightPx();
        ExtractedRoom whole = new ExtractedRoom("Room", "", List.of(
                new Point(0, 0), new Point(w, 0), new Point(w, h), new Point(0, h)), 0.1);
        return new PlanExtraction(List.of(whole), List.of(), null,
                List.of("No vision model configured — trace your plan over the backdrop."));
    }
}

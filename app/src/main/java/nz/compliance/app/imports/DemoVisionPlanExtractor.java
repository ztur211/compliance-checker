package nz.compliance.app.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.compliance.engine.model.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Deterministic, free vision extractor (active under the "demo" profile). Returns a previously
 * captured REAL Claude extraction from classpath {@code demo/extraction.json} when present;
 * otherwise a proportional canned plan sized to the uploaded image, so the backdrop always lines up
 * and the resulting check produces a clear, located violation. No API calls, no key.
 */
@Component
@Profile("demo")
public class DemoVisionPlanExtractor implements VisionPlanExtractor {

    static final String FIXTURE = "demo/extraction.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public PlanExtraction extract(RenderedImage image) {
        PlanExtraction captured = loadCaptured();
        return captured != null ? captured : fallback(image);
    }

    /** The committed real capture, or null if none has been captured yet. */
    PlanExtraction loadCaptured() {
        ClassPathResource res = new ClassPathResource(FIXTURE);
        if (!res.exists()) {
            return null;
        }
        try (InputStream in = res.getInputStream()) {
            return mapper.readValue(in, PlanExtraction.class);
        } catch (Exception e) {
            return null;   // fall back rather than break the demo
        }
    }

    /**
     * Three rooms across the building's width with a SINGLE exit at the far-left wall, sized so the
     * deepest room's open path to that exit far exceeds the 18 m illustrative limit — a guaranteed,
     * located violation for the demo. Coordinates are image pixels; the scale maps the image width to
     * ~55 m so the metre dimensions are realistic. Door labels match room labels so the assembler
     * wires them without proximity guesses.
     */
    PlanExtraction fallback(RenderedImage image) {
        double w = image.widthPx();
        double h = image.heightPx();
        double mpp = 55.0 / w;                 // pixels -> metres (image width ~= 55 m)
        double y0 = h * 0.30, y1 = h * 0.70, ymid = h * 0.5;

        ExtractedRoom lobby = new ExtractedRoom("Lobby", "WB", List.of(
                new Point(w * 0.02, y0), new Point(w * 0.34, y0),
                new Point(w * 0.34, y1), new Point(w * 0.02, y1)), 0.9);
        ExtractedRoom office = new ExtractedRoom("Open office", "WB", List.of(
                new Point(w * 0.34, y0), new Point(w * 0.66, y0),
                new Point(w * 0.66, y1), new Point(w * 0.34, y1)), 0.9);
        ExtractedRoom store = new ExtractedRoom("Back store", "WB", List.of(
                new Point(w * 0.66, y0), new Point(w * 0.98, y0),
                new Point(w * 0.98, y1), new Point(w * 0.66, y1)), 0.9);

        ExtractedDoor exit = new ExtractedDoor(List.of(
                new Point(w * 0.02, ymid - h * 0.06), new Point(w * 0.02, ymid + h * 0.06)),
                List.of("Lobby"), true, 1200.0, 0.9);
        ExtractedDoor d1 = new ExtractedDoor(List.of(
                new Point(w * 0.34, ymid - h * 0.05), new Point(w * 0.34, ymid + h * 0.05)),
                List.of("Lobby", "Open office"), false, 900.0, 0.9);
        ExtractedDoor d2 = new ExtractedDoor(List.of(
                new Point(w * 0.66, ymid - h * 0.05), new Point(w * 0.66, ymid + h * 0.05)),
                List.of("Open office", "Back store"), false, 900.0, 0.9);

        return new PlanExtraction(List.of(lobby, office, store), List.of(exit, d1, d2),
                new ScaleGuess(mpp, "demo", 0.9), List.of("Demo plan (no live vision call)."));
    }
}

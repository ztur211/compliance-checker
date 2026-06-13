package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import java.util.List;

/** A room the vision model found. {@code polygonPx} is in IMAGE PIXELS. */
public record ExtractedRoom(String label, String occupancyTypeGuess, List<Point> polygonPx, double confidence) {
    public ExtractedRoom {
        polygonPx = polygonPx == null ? List.of() : List.copyOf(polygonPx);
    }
}

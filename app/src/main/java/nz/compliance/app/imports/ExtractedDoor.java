package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import java.util.List;

/** A door/opening the vision model found. {@code positionPx} is the 2-point segment in IMAGE PIXELS. */
public record ExtractedDoor(List<Point> positionPx, List<String> connectsRoomLabels,
                            boolean exitGuess, Double clearWidthMmGuess, double confidence) {
    public ExtractedDoor {
        positionPx = positionPx == null ? List.of() : List.copyOf(positionPx);
        connectsRoomLabels = connectsRoomLabels == null ? List.of() : List.copyOf(connectsRoomLabels);
    }
}

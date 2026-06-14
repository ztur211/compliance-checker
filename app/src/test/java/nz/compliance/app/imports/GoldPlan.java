package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;

import java.util.List;

/**
 * Hand-authored ground truth for one plan image, deserialized from a sibling {@code <image>.gold.json}
 * and compared against a live {@link PlanExtraction} by {@link ExtractionScorer}. All coordinates are
 * IMAGE PIXELS of the committed fixture (mind any downscaling). {@code scaleMetresPerPixel} null means
 * "expect a null scale"; {@code scoreScale} null/true scores the scale dimension, false skips it.
 */
public record GoldPlan(List<GoldRoom> rooms, List<GoldDoor> doors, Double scaleMetresPerPixel, Boolean scoreScale) {

    public GoldPlan {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        doors = doors == null ? List.of() : List.copyOf(doors);
    }

    public record GoldRoom(String label, List<Point> polygonPx) {
        public GoldRoom {
            polygonPx = polygonPx == null ? List.of() : List.copyOf(polygonPx);
        }
    }

    public record GoldDoor(List<Point> positionPx, boolean exit) {
        public GoldDoor {
            positionPx = positionPx == null ? List.of() : List.copyOf(positionPx);
        }
    }
}

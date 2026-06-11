package nz.compliance.engine.model;

import java.util.List;

/**
 * A room/space as a simple polygon (metres). {@code occupancyType} keys the
 * occupant-density lookup (e.g. "WB" working/business, "CA" crowd activity).
 */
public record Space(String id, String name, String occupancyType, List<Point> polygon) {
    public Space {
        polygon = polygon == null ? List.of() : List.copyOf(polygon);
    }
}

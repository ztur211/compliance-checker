package nz.compliance.engine.model;

import java.util.List;

/**
 * An opening/door on a space boundary. {@code toSpaceId} == null means it
 * connects to the exterior. {@code position} is the 2-point door segment.
 * {@code clearWidthMillimetres} is the clear opening width. {@code exit} marks
 * a final exit / discharge to a safe place.
 */
public record Door(String id, String fromSpaceId, String toSpaceId,
                   List<Point> position, double clearWidthMillimetres, boolean exit) {
    public Door {
        position = position == null ? List.of() : List.copyOf(position);
    }
}

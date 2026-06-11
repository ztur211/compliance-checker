package nz.compliance.engine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The editable floor-plan geometry document persisted as jsonb. */
public record GeometryDoc(int schemaVersion, List<Space> spaces, List<Door> doors) {

    public GeometryDoc {
        spaces = spaces == null ? List.of() : List.copyOf(spaces);
        doors = doors == null ? List.of() : List.copyOf(doors);
    }

    /** Returns human-readable validation errors; empty means structurally valid. */
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        Set<String> spaceIds = new HashSet<>();
        for (Space s : spaces) {
            if (!spaceIds.add(s.id())) {
                errors.add("duplicate space id: " + s.id());
            }
            if (s.polygon().size() < 3) {
                errors.add("space " + s.id() + " must have at least 3 points");
            }
        }
        Set<String> doorIds = new HashSet<>();
        for (Door d : doors) {
            if (!doorIds.add(d.id())) {
                errors.add("duplicate door id: " + d.id());
            }
            if (!spaceIds.contains(d.fromSpaceId())) {
                errors.add("door " + d.id() + " references unknown fromSpaceId " + d.fromSpaceId());
            }
            if (d.toSpaceId() != null && !spaceIds.contains(d.toSpaceId())) {
                errors.add("door " + d.id() + " references unknown toSpaceId " + d.toSpaceId());
            }
            if (d.position().size() != 2) {
                errors.add("door " + d.id() + " must have exactly 2 position points");
            }
        }
        return errors;
    }
}

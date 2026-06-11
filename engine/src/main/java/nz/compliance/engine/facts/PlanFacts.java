package nz.compliance.engine.facts;

import java.util.List;
import java.util.Optional;

/** Immutable bundle of spatial facts for a whole floor plan. */
public record PlanFacts(List<SpaceFacts> spaces, double totalOccupantLoad,
                        int exitDoorCount, double totalExitWidthMillimetres) {

    public PlanFacts {
        spaces = List.copyOf(spaces);
    }

    public Optional<SpaceFacts> space(String id) {
        return spaces.stream().filter(s -> s.spaceId().equals(id)).findFirst();
    }
}

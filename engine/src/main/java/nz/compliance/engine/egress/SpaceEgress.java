package nz.compliance.engine.egress;

import java.util.List;
import java.util.Optional;

/** Egress outcome for a single space. */
public record SpaceEgress(String spaceId, Optional<Double> openPathLengthMetres,
                          boolean reachesExit, List<String> pathNodeIds) {
    public SpaceEgress {
        pathNodeIds = List.copyOf(pathNodeIds);
    }
}

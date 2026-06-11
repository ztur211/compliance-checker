package nz.compliance.engine.egress;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Egress analysis for a whole plan, keyed by space id. */
public record EgressResult(Map<String, SpaceEgress> bySpace) {

    public EgressResult {
        bySpace = Map.copyOf(bySpace);
    }

    /** The space with the longest open path that still reaches an exit. */
    public Optional<SpaceEgress> worstOpenPath() {
        return bySpace.values().stream()
                .filter(e -> e.openPathLengthMetres().isPresent())
                .max(Comparator.comparingDouble(e -> e.openPathLengthMetres().orElseThrow()));
    }

    /** Spaces with no path to any exit. */
    public List<SpaceEgress> unreachable() {
        return bySpace.values().stream().filter(e -> !e.reachesExit()).toList();
    }
}

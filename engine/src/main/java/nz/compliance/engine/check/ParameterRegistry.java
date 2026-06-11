package nz.compliance.engine.check;

import nz.compliance.engine.egress.EgressResult;
import nz.compliance.engine.egress.SpaceEgress;
import nz.compliance.engine.facts.PlanFacts;
import nz.compliance.engine.facts.SpaceFacts;
import nz.compliance.engine.rules.ParameterKey;

import java.util.Optional;

/** Bridges rule parameters to computed facts/egress. Add a case here to support a new parameter. */
final class ParameterRegistry {

    private ParameterRegistry() {
    }

    static Optional<Double> value(ParameterKey key, PlanFacts facts, EgressResult egress, String spaceId) {
        return switch (key) {
            case OPEN_PATH_LENGTH -> {
                // a degenerate space has no trustworthy geometry/route -> not evaluated (spec §11)
                if (!valid(facts, spaceId)) {
                    yield Optional.empty();
                }
                SpaceEgress se = egress.bySpace().get(spaceId);
                yield se == null ? Optional.empty() : se.openPathLengthMetres();
            }
            case OCCUPANT_LOAD -> valid(facts, spaceId)
                    ? facts.space(spaceId).map(SpaceFacts::occupantLoad)
                    : Optional.empty();
            case EXIT_COUNT -> Optional.of((double) facts.exitDoorCount());
            case EXIT_WIDTH -> Optional.of(facts.totalExitWidthMillimetres());
        };
    }

    private static boolean valid(PlanFacts facts, String spaceId) {
        return facts.space(spaceId).map(SpaceFacts::valid).orElse(false);
    }
}

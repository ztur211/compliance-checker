package nz.compliance.app.imports;

import java.util.List;

/** Whole-plan vision output, in IMAGE PIXELS. {@code scaleGuess} may be null. */
public record PlanExtraction(List<ExtractedRoom> rooms, List<ExtractedDoor> doors,
                             ScaleGuess scaleGuess, List<String> warnings) {
    public PlanExtraction {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        doors = doors == null ? List.of() : List.copyOf(doors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

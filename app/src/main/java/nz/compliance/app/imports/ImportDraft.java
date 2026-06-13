package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import java.util.List;

/**
 * What the import endpoint returns to the frontend. {@code draftGeometryPx} is a GeometryDoc whose
 * coordinates are IMAGE PIXELS (converted to metres only at user Confirm, using the chosen scale).
 */
public record ImportDraft(String backdropPngBase64, int imageWidthPx, int imageHeightPx,
                          GeometryDoc draftGeometryPx, ScaleGuess scaleGuess, List<String> warnings) {
    public ImportDraft {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Space;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Orchestrates an upload into an {@link ImportDraft}: render -> vision extract -> assemble -> classify. */
@Service
public class ImportService {

    private final PlanImageRenderer renderer;
    private final VisionPlanExtractor extractor;
    private final ImportDraftAssembler assembler;
    private final RoomTypeClassifier classifier;

    public ImportService(PlanImageRenderer renderer, VisionPlanExtractor extractor,
                         ImportDraftAssembler assembler, RoomTypeClassifier classifier) {
        this.renderer = renderer;
        this.extractor = extractor;
        this.assembler = assembler;
        this.classifier = classifier;
    }

    public ImportDraft importFrom(byte[] fileBytes) {
        RenderedImage image = renderer.render(fileBytes);
        PlanExtraction extraction = extractor.extract(image);
        return withSuggestedOccupancyTypes(assembler.assemble(image, extraction));
    }

    /**
     * Fills in occupancy types the vision extractor left blank, using the classifier.
     *
     * <p>Only runs when the import resolved a positive scale. The draft geometry is in pixels and
     * the classifier's features are areas in m²; handing it pixel coordinates and calling them
     * metres would produce a confident, meaningless answer, and occupancy type feeds occupant load,
     * so that answer would change a compliance verdict. No scale means no suggestion.
     *
     * <p>An occupancy type the vision extractor did supply is never overwritten. This layer only
     * fills gaps.
     */
    private ImportDraft withSuggestedOccupancyTypes(ImportDraft draft) {
        ScaleGuess scale = draft.scaleGuess();
        if (scale == null || scale.metresPerPixel() <= 0) {
            return draft;
        }

        GeometryDoc geometry = draft.draftGeometryPx();
        Map<String, String> suggestions = classifier.classify(geometry, scale.metresPerPixel());
        if (suggestions.isEmpty()) {
            return draft;
        }

        List<Space> spaces = new ArrayList<>(geometry.spaces().size());
        int filled = 0;
        for (Space s : geometry.spaces()) {
            String suggested = suggestions.get(s.id());
            boolean blank = s.occupancyType() == null || s.occupancyType().isBlank();
            if (blank && suggested != null) {
                spaces.add(new Space(s.id(), s.name(), suggested, s.polygon()));
                filled++;
            } else {
                spaces.add(s);
            }
        }
        if (filled == 0) {
            return draft;
        }

        List<String> warnings = new ArrayList<>(draft.warnings());
        // Surfaced to the reviewer rather than applied silently: a suggested occupancy type is a
        // model's opinion about a number that decides whether the building passes.
        warnings.add("Suggested an occupancy type for " + filled
                + " room(s) that had none on the plan. Review each before confirming.");

        return new ImportDraft(draft.backdropPngBase64(), draft.imageWidthPx(), draft.imageHeightPx(),
                new GeometryDoc(geometry.schemaVersion(), spaces, geometry.doors()),
                draft.scaleGuess(), warnings);
    }
}

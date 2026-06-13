package nz.compliance.app.imports;

import org.springframework.stereotype.Service;

/** Orchestrates an upload into an {@link ImportDraft}: render -> vision extract -> assemble. */
@Service
public class ImportService {

    private final PlanImageRenderer renderer;
    private final VisionPlanExtractor extractor;
    private final ImportDraftAssembler assembler;

    public ImportService(PlanImageRenderer renderer, VisionPlanExtractor extractor,
                         ImportDraftAssembler assembler) {
        this.renderer = renderer;
        this.extractor = extractor;
        this.assembler = assembler;
    }

    public ImportDraft importFrom(byte[] fileBytes) {
        RenderedImage image = renderer.render(fileBytes);
        PlanExtraction extraction = extractor.extract(image);
        return assembler.assemble(image, extraction);
    }
}

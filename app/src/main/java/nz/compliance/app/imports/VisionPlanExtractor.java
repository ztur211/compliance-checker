package nz.compliance.app.imports;

/** Seam over the vision LLM so the app runs without an API key and tests can stub it. */
public interface VisionPlanExtractor {
    PlanExtraction extract(RenderedImage image);
}

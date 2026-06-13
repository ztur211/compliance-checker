package nz.compliance.app.imports;

/** A pixels->metres scale guess. {@code source} is e.g. "scale-bar", "dimension", "other". */
public record ScaleGuess(double metresPerPixel, String source, double confidence) {}

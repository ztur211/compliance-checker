package nz.compliance.app.imports;

/** A normalised PNG render of an uploaded plan, plus its pixel dimensions. */
public record RenderedImage(byte[] pngBytes, int widthPx, int heightPx) {}

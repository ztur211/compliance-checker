package nz.compliance.app.imports;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Renders an uploaded PDF (first page) or raster image into a normalised PNG. */
@Component
public class PlanImageRenderer {

    private static final float PDF_DPI = 150f;

    public RenderedImage render(byte[] bytes) {
        try {
            BufferedImage image = isPdf(bytes) ? renderPdfFirstPage(bytes) : readImage(bytes);
            return new RenderedImage(toPng(image), image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the uploaded plan", e);
        }
    }

    private static boolean isPdf(byte[] b) {
        return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    private static BufferedImage renderPdfFirstPage(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFRenderer(doc).renderImageWithDPI(0, PDF_DPI, ImageType.RGB);
        }
    }

    private static BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
        if (bi == null) {
            throw new IOException("unsupported image format");
        }
        return bi;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }
}

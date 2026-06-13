package nz.compliance.app.imports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PlanImageRendererTest {

    private final PlanImageRenderer renderer = new PlanImageRenderer();

    @Test
    void rendersAnImagePassthroughKeepingDimensions() throws Exception {
        BufferedImage bi = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);

        RenderedImage out = renderer.render(bos.toByteArray());

        assertThat(out.widthPx()).isEqualTo(120);
        assertThat(out.heightPx()).isEqualTo(90);
        assertThat(out.pngBytes()).isNotEmpty();
    }

    @Test
    void rendersFirstPageOfAPdf() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage(PDRectangle.A4));
            pdf.save(bos);
        }

        RenderedImage out = renderer.render(bos.toByteArray());

        assertThat(out.widthPx()).isGreaterThan(0);
        assertThat(out.heightPx()).isGreaterThan(0);
        assertThat(out.pngBytes()).isNotEmpty();
    }
}

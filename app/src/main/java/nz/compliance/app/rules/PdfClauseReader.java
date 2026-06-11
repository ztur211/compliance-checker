package nz.compliance.app.rules;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class PdfClauseReader {

    private final ClauseChunker chunker;

    public PdfClauseReader(ClauseChunker chunker) {
        this.chunker = chunker;
    }

    public List<Clause> read(InputStream pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.readAllBytes())) {
            String text = new PDFTextStripper().getText(doc);
            return chunker.chunk(text);
        }
    }
}

package nz.compliance.app.imports;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/imports")
    public ImportDraft create(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no file uploaded");
        }
        try {
            return imports.importFrom(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (UncheckedIOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not read the uploaded plan");
        }
    }
}

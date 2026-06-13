package nz.compliance.app.imports;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Maps an over-limit multipart upload to a clear 413 instead of a generic 500.
 * The exception is raised during request parsing, so this advice is global (not
 * scoped to {@link ImportController}) to be sure it is reached before a handler is matched.
 */
@RestControllerAdvice
public class ImportUploadAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "The uploaded plan is too large. Please upload a smaller PDF or image."));
    }
}

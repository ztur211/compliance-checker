package nz.compliance.app.imports;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportUploadAdviceTest {

    @Test
    void mapsAnOversizeUploadTo413WithAClearMessage() {
        ResponseEntity<Map<String, String>> response =
                new ImportUploadAdvice().tooLarge(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).containsIgnoringCase("too large");
    }
}

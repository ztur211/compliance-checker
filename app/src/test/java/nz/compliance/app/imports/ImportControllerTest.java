package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
class ImportControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ImportService imports;

    @Test
    void returnsDraftForAnUploadedFile() throws Exception {
        ImportDraft draft = new ImportDraft("base64png", 200, 100,
                new GeometryDoc(1, List.of(), List.of()), null, List.of());
        when(imports.importFrom(any())).thenReturn(draft);

        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/imports").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageWidthPx").value(200))
                .andExpect(jsonPath("$.backdropPngBase64").value("base64png"));
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[]{});
        mvc.perform(multipart("/api/imports").file(empty))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnreadablePlanWith400() throws Exception {
        when(imports.importFrom(any())).thenThrow(new UncheckedIOException(new IOException("bad image")));
        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/imports").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsAnUnreadableUploadStreamTo400() throws Exception {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getBytes()).thenThrow(new IOException("stream broke"));

        ImportController controller = new ImportController(mock(ImportService.class));

        assertThatThrownBy(() -> controller.create(broken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

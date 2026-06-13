package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
}

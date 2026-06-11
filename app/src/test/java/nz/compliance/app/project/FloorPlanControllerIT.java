package nz.compliance.app.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class FloorPlanControllerIT extends PostgresIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"P\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    @Test
    void createSaveAndReloadFloorPlan() throws Exception {
        String projectId = createProject();

        String fpBody = mockMvc.perform(post("/api/projects/" + projectId + "/floorplans")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"L1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fpId = json.readTree(fpBody).get("id").asText();

        String geometry = """
            {"name":"L1","riskGroup":"WB","sprinklered":true,"escapeHeightMetres":3.0,
             "geometry":{"schemaVersion":1,
               "spaces":[{"id":"s1","name":"Office","occupancyType":"WB",
                 "polygon":[{"x":0,"y":0},{"x":10,"y":0},{"x":10,"y":10},{"x":0,"y":10}]}],
               "doors":[{"id":"d1","fromSpaceId":"s1","toSpaceId":null,
                 "position":[{"x":0,"y":4},{"x":0,"y":6}],"clearWidthMillimetres":1200,"exit":true}]}}""";

        mockMvc.perform(put("/api/floorplans/" + fpId)
                        .contentType(MediaType.APPLICATION_JSON).content(geometry))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/floorplans/" + fpId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geometry.spaces[0].id").value("s1"))
                .andExpect(jsonPath("$.riskGroup").value("WB"));
    }

    @Test
    void rejectsInvalidGeometryWith422() throws Exception {
        String projectId = createProject();
        String fpBody = mockMvc.perform(post("/api/projects/" + projectId + "/floorplans")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"L1\"}"))
                .andReturn().getResponse().getContentAsString();
        String fpId = json.readTree(fpBody).get("id").asText();

        // space with only 2 points -> invalid
        String bad = """
            {"name":"L1","geometry":{"schemaVersion":1,
              "spaces":[{"id":"s1","name":"x","occupancyType":"WB",
                "polygon":[{"x":0,"y":0},{"x":1,"y":1}]}],"doors":[]}}""";

        mockMvc.perform(put("/api/floorplans/" + fpId)
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isUnprocessableEntity());
    }
}

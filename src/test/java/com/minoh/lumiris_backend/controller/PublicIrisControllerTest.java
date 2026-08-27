package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.service.scoring.IrisMethodologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicIrisControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Contenu statique : pas de mock, on veut vérifier le texte réellement servi.
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicIrisController(new IrisMethodologyService())).build();
    }

    @Test
    void getMethodology_returnsTheFourWeightedAxes() throws Exception {
        mockMvc.perform(get("/public/iris/methodology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v3"))
                .andExpect(jsonPath("$.sections.length()").value(4))
                .andExpect(jsonPath("$.sections[0].key").value("transparency"))
                .andExpect(jsonPath("$.sections[0].weightPercent").value(40))
                .andExpect(jsonPath("$.sections[1].key").value("craftsmanship"))
                .andExpect(jsonPath("$.sections[1].weightPercent").value(25))
                .andExpect(jsonPath("$.sections[2].key").value("impact"))
                .andExpect(jsonPath("$.sections[2].weightPercent").value(25))
                .andExpect(jsonPath("$.sections[3].key").value("repairability"))
                .andExpect(jsonPath("$.sections[3].weightPercent").value(10));
    }

    @Test
    void getMethodology_weightsSumToOneHundred() throws Exception {
        // Garde-fou : les quatre axes forment toute la note, il n'y a plus de section hors moyenne.
        mockMvc.perform(get("/public/iris/methodology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[*].weightPercent").value(
                        org.hamcrest.Matchers.hasItems(40, 25, 25, 10)));
    }

    @Test
    void getMethodology_exposesTheFiveIrisGradeThresholds() throws Exception {
        mockMvc.perform(get("/public/iris/methodology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grades.length()").value(5))
                .andExpect(jsonPath("$.grades[0].grade").value("A"))
                .andExpect(jsonPath("$.grades[0].minScore").value(80))
                .andExpect(jsonPath("$.grades[4].grade").value("E"))
                .andExpect(jsonPath("$.grades[4].minScore").value(0));
    }

    @Test
    void getMethodology_isPubliclyCacheable() throws Exception {
        mockMvc.perform(get("/public/iris/methodology"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"));
    }
}

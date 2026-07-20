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
    void getMethodology_returnsSectionsOrderedByWeightThenCap() throws Exception {
        mockMvc.perform(get("/public/iris/methodology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2"))
                .andExpect(jsonPath("$.sections.length()").value(5))
                .andExpect(jsonPath("$.sections[0].key").value("transparency"))
                .andExpect(jsonPath("$.sections[0].weightPercent").value(40))
                .andExpect(jsonPath("$.sections[1].key").value("craftsmanship"))
                .andExpect(jsonPath("$.sections[1].weightPercent").value(25))
                .andExpect(jsonPath("$.sections[2].key").value("impact"))
                .andExpect(jsonPath("$.sections[2].weightPercent").value(25))
                .andExpect(jsonPath("$.sections[3].key").value("repairability"))
                .andExpect(jsonPath("$.sections[3].weightPercent").value(10))
                .andExpect(jsonPath("$.sections[4].key").value("regulatory-cap"))
                // Section hors moyenne pondérée : le poids doit être absent, pas à 0.
                .andExpect(jsonPath("$.sections[4].weightPercent").doesNotExist());
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

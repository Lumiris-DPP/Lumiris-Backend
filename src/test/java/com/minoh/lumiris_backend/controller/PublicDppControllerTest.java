package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.DppPublicJsonLdResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.service.DppEventService;
import com.minoh.lumiris_backend.service.DppFormService;
import com.minoh.lumiris_backend.service.GeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicDppControllerTest {

    private DppFormService dppFormService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dppFormService = mock(DppFormService.class);
        DppEventService dppEventService = mock(DppEventService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicDppController(dppFormService, dppEventService))
                .build();
    }

    @Test
    void findPublicJsonLd_returnsJsonLdMediaTypeAndPublicProjection() throws Exception {
        DppForm form = new DppForm();
        form.setPublicCode("SEED0001");
        form.setProductName("Veste en lin");
        form.setOriginCountry("France");
        form.setManufacturedAt("2026-08-28");
        form.setSku("SKU-INTERNE");

        DppFormMapper mapper = new DppFormMapper(mock(GeocodingService.class));
        String canonicalId = "http://localhost/public/dpp_forms/SEED0001/jsonld";
        DppPublicJsonLdResponse response = mapper.toPublicJsonLd(
                form,
                null,
                List.of(),
                null,
                null,
                canonicalId
        );
        when(dppFormService.findPublicJsonLd("SEED0001", canonicalId)).thenReturn(response);

        mockMvc.perform(get("/public/dpp_forms/SEED0001/jsonld"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/ld+json"))
                .andExpect(jsonPath("$['@context']['@vocab']").value("https://schema.org/"))
                .andExpect(jsonPath("$['@context'].image['@type']").value("@id"))
                .andExpect(jsonPath("$['@context'].url['@type']").value("@id"))
                .andExpect(jsonPath("$['@context'].dateCreated['@type']").value("xsd:dateTime"))
                .andExpect(jsonPath("$['@context'].productionDate['@type']").value("xsd:date"))
                .andExpect(jsonPath("$['@id']").value(canonicalId))
                .andExpect(jsonPath("$['@type'][0]").value("Product"))
                .andExpect(jsonPath("$.publicCode").value("SEED0001"))
                .andExpect(jsonPath("$.name").value("Veste en lin"))
                .andExpect(jsonPath("$.countryOfOrigin['@type']").value("Country"))
                .andExpect(jsonPath("$.countryOfOrigin.name").value("France"))
                .andExpect(jsonPath("$.sku").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.fileId").doesNotExist());
    }
}

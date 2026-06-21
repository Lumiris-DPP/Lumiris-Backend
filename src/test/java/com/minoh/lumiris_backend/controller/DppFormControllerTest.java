package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.service.DppFormService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DppFormControllerTest {

    @Mock
    private DppFormService dppFormService;

    @InjectMocks
    private DppFormController dppFormController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dppFormController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private DppFormResponse sampleResponse(UUID id) {
        return new DppFormResponse(id, "Pull Merino", "sweater", "CHE-001", BigDecimal.valueOf(180), "EUR", "Draft", Instant.now(), Instant.now());
    }

    @Test
    void create_shouldReturn201_withBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppFormService.create(any())).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/api/dpp-forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DppFormRequest("Pull Merino", "sweater", "CHE-001", BigDecimal.valueOf(180), "EUR", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Pull Merino"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        verify(dppFormService).create(any());
    }

    @Test
    void create_shouldReturn201_withEmptyBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppFormService.create(any())).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/api/dpp-forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_shouldReturn201_withNoBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppFormService.create(any())).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/api/dpp-forms"))
                .andExpect(status().isCreated());
    }

    @Test
    void findAll_shouldReturn200_withList() throws Exception {
        when(dppFormService.findAll()).thenReturn(List.of(sampleResponse(UUID.randomUUID())));

        mockMvc.perform(get("/api/dpp-forms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Pull Merino"));

        verify(dppFormService).findAll();
    }

    @Test
    void findById_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppFormService.findById(id)).thenReturn(sampleResponse(id));

        mockMvc.perform(get("/api/dpp-forms/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(dppFormService).findById(id);
    }

    @Test
    void patch_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppFormService.patch(eq(id), any())).thenReturn(sampleResponse(id));

        mockMvc.perform(patch("/api/dpp-forms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DppFormRequest(null, null, "CHE-002", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(dppFormService).patch(eq(id), any());
    }
}

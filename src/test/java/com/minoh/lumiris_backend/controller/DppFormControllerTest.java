package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.MaterialRequest;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.exception.GlobalExceptionHandler;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.service.DppPublicationService;
import com.minoh.lumiris_backend.service.DppQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DppFormControllerTest {

    @Mock
    private DppPublicationService dppPublicationService;

    @Mock
    private DppQueryService dppQueryService;

    @InjectMocks
    private DppFormController dppFormController;

    private MockMvc mockMvc;

    private static final String USER_EMAIL = "artisan@test.com";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        UserDetails userDetails = User.withUsername(USER_EMAIL).password("x").roles("ARTISAN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        mockMvc = MockMvcBuilders.standaloneSetup(dppFormController)
                .setControllerAdvice(new GlobalExceptionHandler(new MockEnvironment()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_shouldReturn201_withBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppPublicationService.create(any(), anyMap(), eq(USER_EMAIL), eq(false))).thenReturn(new DppFormCreatedResponse(id));

        DppFormRequest request = new DppFormRequest(
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("S", "M"), List.of("Écru"),
                List.of(), List.of(), null,
                "2026-01-01", null, null, null, true,
                null, null, null, null, null, null, 1,
                null, null
        );

        MockMultipartFile dataPart = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        // create returns DppFormCreatedResponse(id) only — the full DPP is fetched via GET /{id}.
        mockMvc.perform(multipart("/api/dpp-forms").file(dataPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.id").isNotEmpty());

        verify(dppPublicationService).create(any(), anyMap(), eq(USER_EMAIL), eq(false));
    }

    @Test
    void duplicate_shouldReturn201_withCreatedDraftId() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID copyId = UUID.randomUUID();
        when(dppPublicationService.duplicate(sourceId, USER_EMAIL)).thenReturn(new DppFormCreatedResponse(copyId));

        mockMvc.perform(post("/api/dpp-forms/{id}/duplicate", sourceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(copyId.toString()));

        verify(dppPublicationService).duplicate(sourceId, USER_EMAIL);
    }

    @Test
    void duplicate_otherUsersDpp_shouldReturn404() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(dppPublicationService.duplicate(sourceId, USER_EMAIL))
                .thenThrow(new ResourceNotFoundException("DPP not found"));

        mockMvc.perform(post("/api/dpp-forms/{id}/duplicate", sourceId))
                .andExpect(status().isNotFound());

        verify(dppPublicationService).duplicate(sourceId, USER_EMAIL);
    }

    // ── validation du corps (Bean Validation sur la part multipart "data") ────

    @Test
    void create_shouldReturn400_andNotReachTheService_whenFieldsAreOutOfBounds() throws Exception {
        DppFormRequest request = new DppFormRequest(
                "Pull Merino", null, "top", "FR",
                null, null,
                List.of(), List.of(), null,
                "2026-01-01", null, "ABC-123", null, true,
                0, 150, null, null, null, null, 1,
                null, null
        );

        mockMvc.perform(multipart("/api/dpp-forms").file(dataPart(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("gtin")))
                .andExpect(jsonPath("$.errors", hasKey("weightGrams")))
                .andExpect(jsonPath("$.errors", hasKey("recycledPct")));

        verify(dppPublicationService, never()).create(any(), anyMap(), anyString(), anyBoolean());
    }

    @Test
    void create_shouldValidateEachMaterial() throws Exception {
        DppFormRequest request = new DppFormRequest(
                "Pull Merino", null, "top", "FR",
                null, null,
                List.of(new MaterialRequest(" ", 120, "France")), List.of(), null,
                null, null, null, null, true,
                null, null, null, null, null, null, 1,
                null, null
        );

        mockMvc.perform(multipart("/api/dpp-forms").file(dataPart(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("materials[0].fiber")))
                .andExpect(jsonPath("$.errors", hasKey("materials[0].percentage")));

        verify(dppPublicationService, never()).create(any(), anyMap(), anyString(), anyBoolean());
    }

    @Test
    void update_shouldReturn400_whenTheManufacturingDateIsMalformed() throws Exception {
        UUID id = UUID.randomUUID();
        DppFormRequest request = new DppFormRequest(
                "Pull Merino", null, "top", "FR",
                null, null,
                List.of(), List.of(), null,
                "15/03/2026", null, null, null, true,
                null, null, null, null, null, null, 1,
                null, null
        );

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/dpp-forms/{id}", id).file(dataPart(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.manufacturedAt", containsString("AAAA-MM-JJ")));

        verify(dppPublicationService, never()).update(any(), any(), anyMap(), anyString());
    }

    // Un brouillon partiel (champs vides, dont un GTIN effacé) doit rester enregistrable.
    @Test
    void create_draft_shouldAcceptEmptyOptionalFields() throws Exception {
        when(dppPublicationService.create(any(), anyMap(), eq(USER_EMAIL), eq(true)))
                .thenReturn(new DppFormCreatedResponse(UUID.randomUUID()));
        DppFormRequest request = new DppFormRequest(
                "", null, null, null,
                null, null,
                null, null, null,
                "", null, "", null, null,
                null, null, null, null, null, null, null,
                null, null
        );

        mockMvc.perform(multipart("/api/dpp-forms").file(dataPart(request)).param("draft", "true"))
                .andExpect(status().isCreated());
    }

    private MockMultipartFile dataPart(DppFormRequest request) throws Exception {
        return new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );
    }
}

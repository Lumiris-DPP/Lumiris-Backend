package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.service.DppFormService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DppFormControllerTest {

    @Mock
    private DppFormService dppFormService;

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
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private DppFormResponse sampleResponse(UUID id) {
        return new DppFormResponse(
                id, Instant.now(), DppStatus.VALID,
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("S", "M", "L"), List.of("Écru"),
                null, List.of(), List.of(), List.of(),
                "2026-01-01", "LOT-001", null, "SKU-001", true,
                30, "2 ans", true, "Rapporter en boutique"
        );
    }

    @Test
    void create_shouldReturn201_withBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(dppFormService.create(any(), eq(USER_EMAIL))).thenReturn(sampleResponse(id));

        DppFormRequest request = new DppFormRequest(
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("S", "M"), List.of("Écru"), null,
                List.of(), List.of(), List.of(),
                "2026-01-01", null, null, null, true,
                null, null, null, null
        );

        mockMvc.perform(post("/api/dpp-forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Pull Merino"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        verify(dppFormService).create(any(), eq(USER_EMAIL));
    }

    @Test
    void create_shouldReturn201_withEmptyBody() throws Exception {
        when(dppFormService.create(any(), eq(USER_EMAIL))).thenReturn(sampleResponse(UUID.randomUUID()));

        mockMvc.perform(post("/api/dpp-forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }
}

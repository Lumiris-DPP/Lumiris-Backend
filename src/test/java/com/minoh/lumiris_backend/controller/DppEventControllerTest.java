package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.entity.DppEventActorType;
import com.minoh.lumiris_backend.service.DppEventService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DppEventControllerTest {

    @Mock
    private DppEventService dppEventService;

    @InjectMocks
    private DppEventController dppEventController;

    private MockMvc mockMvc;

    private static final String USER_EMAIL = "artisan@test.com";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        UserDetails userDetails = User.withUsername(USER_EMAIL).password("x").roles("ARTISAN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        mockMvc = MockMvcBuilders.standaloneSetup(dppEventController)
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
        UUID dppId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-05-01T10:00:00Z");
        DppEventRequest request = new DppEventRequest(occurredAt, "Remplacement de la fermeture éclair", DppEventActorType.REPAIRER, null, null);

        when(dppEventService.create(eq(dppId), any(), eq(USER_EMAIL)))
                .thenReturn(new DppEventResponse(eventId, occurredAt, request.description(), request.actorType(), null, null, null, null, Instant.now()));

        mockMvc.perform(post("/api/dpp-forms/{id}/events", dppId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.description").value("Remplacement de la fermeture éclair"))
                .andExpect(jsonPath("$.actorType").value("REPAIRER"));

        verify(dppEventService).create(eq(dppId), any(), eq(USER_EMAIL));
    }

    @Test
    void create_shouldReturn400_whenDescriptionBlank() throws Exception {
        UUID dppId = UUID.randomUUID();
        DppEventRequest request = new DppEventRequest(Instant.parse("2026-05-01T10:00:00Z"), "  ", DppEventActorType.CONSUMER, null, null);

        mockMvc.perform(post("/api/dpp-forms/{id}/events", dppId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());

        verify(dppEventService, never()).create(any(), any(), any());
    }

    @Test
    void findAll_shouldReturn200_withEvents() throws Exception {
        UUID dppId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(dppEventService.findAllByDppFormId(dppId, USER_EMAIL)).thenReturn(List.of(
                new DppEventResponse(eventId, Instant.parse("2026-05-01T10:00:00Z"),
                        "Vente au client final", DppEventActorType.RETAILER, null, null, null, null, Instant.now())
        ));

        mockMvc.perform(get("/api/dpp-forms/{id}/events", dppId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].actorType").value("RETAILER"));
    }
}

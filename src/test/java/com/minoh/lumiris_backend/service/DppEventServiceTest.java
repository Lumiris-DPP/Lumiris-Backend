package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.entity.DppEvent;
import com.minoh.lumiris_backend.entity.DppEventActorType;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppEventMapper;
import com.minoh.lumiris_backend.repository.DppEventRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DppEventServiceTest {

    @Mock
    private DppEventRepository dppEventRepository;

    @Mock
    private DppFormRepository dppFormRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private RepairRequestRepository repairRequestRepository;

    @Spy
    private DppEventMapper dppEventMapper;

    @InjectMocks
    private DppEventService service;

    private static final String USER_EMAIL = "artisan@test.com";

    private User user;
    private DppForm form;
    private UUID formId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);

        formId = UUID.randomUUID();
        form = new DppForm();
        form.setId(formId);
        form.setUser(user);
        form.setPublicCode("ABCD1234");

        lenient().when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
        lenient().when(dppFormRepository.findById(formId)).thenReturn(Optional.of(form));
        lenient().when(geocodingService.geocode(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void create_shouldPersistAndReturnResponse() {
        Instant occurredAt = Instant.parse("2026-05-01T10:00:00Z");
        DppEventRequest request = new DppEventRequest(occurredAt, "Remplacement de la semelle", DppEventActorType.REPAIRER, "Lyon", "France");
        when(dppEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DppEventResponse response = service.create(formId, request, USER_EMAIL);

        assertThat(response.occurredAt()).isEqualTo(occurredAt);
        assertThat(response.description()).isEqualTo("Remplacement de la semelle");
        assertThat(response.actorType()).isEqualTo(DppEventActorType.REPAIRER);
        verify(dppEventRepository).save(any(DppEvent.class));
    }

    @Test
    void create_shouldThrowNotFound_whenUserIsNotOwner() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@test.com");
        when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherUser));

        DppEventRequest request = new DppEventRequest(Instant.now(), "Tentative non autorisée", DppEventActorType.CONSUMER, null, null);

        assertThatThrownBy(() -> service.create(formId, request, "other@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("DPP not found");
        verify(dppEventRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowNotFound_whenDppDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(dppFormRepository.findById(unknownId)).thenReturn(Optional.empty());

        DppEventRequest request = new DppEventRequest(Instant.now(), "Événement", DppEventActorType.CONSUMER, null, null);

        assertThatThrownBy(() -> service.create(unknownId, request, USER_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAllByDppFormId_shouldReturnMappedEvents() {
        DppEvent event = new DppEvent(form, Instant.parse("2026-05-01T10:00:00Z"),
                "Vente au client final", DppEventActorType.RETAILER, null, null, null, null);
        when(dppEventRepository.findByDppFormIdOrderByOccurredAtDesc(formId)).thenReturn(List.of(event));

        List<DppEventResponse> responses = service.findAllByDppFormId(formId, USER_EMAIL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).description()).isEqualTo("Vente au client final");
        assertThat(responses.get(0).actorType()).isEqualTo(DppEventActorType.RETAILER);
    }

    @Test
    void findAllByPublicCode_shouldReturnEvents_withoutOwnershipCheck() {
        when(dppFormRepository.findByPublicCode("ABCD1234")).thenReturn(Optional.of(form));
        DppEvent event = new DppEvent(form, Instant.parse("2026-05-01T10:00:00Z"),
                "Recyclage du produit", DppEventActorType.RECYCLER, null, null, null, null);
        when(dppEventRepository.findByDppFormIdOrderByOccurredAtDesc(formId)).thenReturn(List.of(event));

        List<DppEventResponse> responses = service.findAllByPublicCode("ABCD1234");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).actorType()).isEqualTo(DppEventActorType.RECYCLER);
    }

    @Test
    void findAllByPublicCode_shouldThrowNotFound_whenCodeUnknown() {
        when(dppFormRepository.findByPublicCode("XXXXXXXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAllByPublicCode("XXXXXXXX"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

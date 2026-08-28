package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.WardrobeSyncRequest;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.WardrobeItem;
import com.minoh.lumiris_backend.entity.WardrobeItemKind;
import com.minoh.lumiris_backend.entity.WardrobeItemOrigin;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WardrobeSyncServiceTest {

    private static final String EMAIL = "camille@example.com";
    private static final UUID USER_ID = UUID.fromString("8f8a22c2-6b92-44f9-9dc1-3dcf81c9419b");

    private UserRepository userRepository;
    private WardrobeItemRepository wardrobeItemRepository;
    private WardrobeSyncService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        wardrobeItemRepository = mock(WardrobeItemRepository.class);
        service = new WardrobeSyncService(userRepository, wardrobeItemRepository);

        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        when(userRepository.getByEmail(EMAIL)).thenReturn(user);
        when(wardrobeItemRepository.findOwnedWithPassport(USER_ID)).thenReturn(List.of());
    }

    @Test
    void upsertsAUserItemWithAStableClientKey() {
        Instant addedAt = Instant.parse("2026-08-28T10:00:00Z");
        WardrobeSyncRequest.Upsert input = new WardrobeSyncRequest.Upsert(
                "manual:0ebc4076-7ce8-4eb1-8476-ed49d40ada61",
                WardrobeItemKind.MANUAL,
                addedAt,
                Map.of(
                        "id", "0ebc4076-7ce8-4eb1-8476-ed49d40ada61",
                        "sector", "textile",
                        "productName", "Veste bleue"
                )
        );
        when(wardrobeItemRepository.findByUser_IdAndOriginAndClientKeyIn(
                USER_ID, WardrobeItemOrigin.USER, List.of(input.clientKey()))).thenReturn(List.of());

        service.sync(EMAIL, new WardrobeSyncRequest(List.of(input), List.of()));

        ArgumentCaptor<WardrobeItem> captor = ArgumentCaptor.forClass(WardrobeItem.class);
        verify(wardrobeItemRepository).save(captor.capture());
        WardrobeItem saved = captor.getValue();
        assertThat(saved.getUser().getId()).isEqualTo(USER_ID);
        assertThat(saved.getOrigin()).isEqualTo(WardrobeItemOrigin.USER);
        assertThat(saved.getClientKey()).isEqualTo(input.clientKey());
        assertThat(saved.getKind()).isEqualTo(WardrobeItemKind.MANUAL);
        assertThat(saved.getAcquiredAt()).isEqualTo(addedAt);
        assertThat(saved.getPayload()).containsEntry("productName", "Veste bleue");
    }

    @Test
    void deletionsAreRestrictedToUserManagedRows() {
        service.sync(EMAIL, new WardrobeSyncRequest(List.of(), List.of("public:ABC123")));

        verify(wardrobeItemRepository).deleteByUserAndOriginAndClientKeys(
                USER_ID, WardrobeItemOrigin.USER, List.of("public:ABC123"));
        verify(wardrobeItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAClientKeyThatDoesNotMatchItsPayload() {
        WardrobeSyncRequest.Upsert input = new WardrobeSyncRequest.Upsert(
                "manual:wrong-id",
                WardrobeItemKind.MANUAL,
                Instant.now(),
                Map.of("id", "right-id", "sector", "textile", "productName", "Pull")
        );
        when(wardrobeItemRepository.findByUser_IdAndOriginAndClientKeyIn(
                USER_ID, WardrobeItemOrigin.USER, List.of(input.clientKey()))).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync(EMAIL, new WardrobeSyncRequest(List.of(input), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Clé de pièce incohérente");
        verify(wardrobeItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateUpsertsBeforeWritingAnything() {
        WardrobeSyncRequest.Upsert input = new WardrobeSyncRequest.Upsert(
                "gtin:123456789",
                WardrobeItemKind.EXTERNAL_DPP,
                Instant.now(),
                Map.of("gtin", "123456789")
        );

        assertThatThrownBy(() -> service.sync(
                EMAIL, new WardrobeSyncRequest(List.of(input, input), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("qu'une fois");
        verify(wardrobeItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

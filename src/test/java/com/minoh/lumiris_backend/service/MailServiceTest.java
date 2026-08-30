package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.EmailOutboxResponse;
import com.minoh.lumiris_backend.entity.EmailOutbox;
import com.minoh.lumiris_backend.entity.EmailOutboxStatus;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private EmailOutboxRepository emailOutboxRepository;

    @InjectMocks
    private MailService service;

    private final UUID id = UUID.randomUUID();

    @Test
    void retryDead_shouldResetToPending_whenStatusIsDead() {
        EmailOutbox outbox = deadOutbox();
        when(emailOutboxRepository.findById(id)).thenReturn(Optional.of(outbox));
        when(emailOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        EmailOutboxResponse response = service.retryDead(id);

        ArgumentCaptor<EmailOutbox> saved = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(saved.getValue().getAttempts()).isZero();
        assertThat(saved.getValue().getNextAttemptAt()).isAfterOrEqualTo(before);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.attempts()).isZero();
    }

    @Test
    void retryDead_shouldThrowNotFound_whenIdUnknown() {
        when(emailOutboxRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryDead(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(emailOutboxRepository, never()).save(any());
    }

    @Test
    void retryDead_shouldThrowConflict_whenStatusIsNotDead() {
        EmailOutbox outbox = deadOutbox();
        outbox.setStatus(EmailOutboxStatus.PENDING);
        when(emailOutboxRepository.findById(id)).thenReturn(Optional.of(outbox));

        assertThatThrownBy(() -> service.retryDead(id))
                .isInstanceOf(ConflictException.class);
        verify(emailOutboxRepository, never()).save(any());
    }

    @Test
    void list_shouldPassThroughStatusAndRecipientFilters() {
        EmailOutbox outbox = deadOutbox();
        when(emailOutboxRepository.search(eq(EmailOutboxStatus.SENT), eq("client"), any()))
                .thenReturn(List.of(outbox));

        List<EmailOutboxResponse> result = service.list(EmailOutboxStatus.SENT, "client", 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
    }

    @Test
    void list_shouldTreatBlankRecipientAsNoFilter() {
        when(emailOutboxRepository.search(isNull(), isNull(), any())).thenReturn(List.of());

        service.list(null, "  ", 0);

        verify(emailOutboxRepository).search(isNull(), isNull(), any());
    }

    private EmailOutbox deadOutbox() {
        EmailOutbox outbox = new EmailOutbox();
        outbox.setId(id);
        outbox.setRecipientEmail("client@lumiris.app");
        outbox.setSubject("Paiement confirmé");
        outbox.setTemplate("email/payment-success");
        outbox.setVariables(Map.of("name", "Camille"));
        outbox.setStatus(EmailOutboxStatus.DEAD);
        outbox.setAttempts(5);
        outbox.setMaxAttempts(5);
        outbox.setLastError("Timeout Resend");
        return outbox;
    }
}

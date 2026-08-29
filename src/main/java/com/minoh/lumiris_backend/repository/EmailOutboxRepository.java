package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.EmailOutbox;
import com.minoh.lumiris_backend.entity.EmailOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    List<EmailOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            EmailOutboxStatus status, Instant now, Pageable pageable);

    List<EmailOutbox> findByStatusOrderByCreatedAtDesc(EmailOutboxStatus status, Pageable pageable);
}

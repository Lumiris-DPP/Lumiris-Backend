package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.EmailOutbox;
import com.minoh.lumiris_backend.entity.EmailOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    List<EmailOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            EmailOutboxStatus status, Instant now, Pageable pageable);

    List<EmailOutbox> findByStatusOrderByCreatedAtDesc(EmailOutboxStatus status, Pageable pageable);

    // Log d'envoi admin : les deux filtres sont optionnels (null = pas de filtre). Le cast est
    // nécessaire : sans lui, Hibernate mal-type :recipientEmail quand il est null (Postgres
    // résout concat(...) en bytea et lower() échoue avec "function lower(bytea) does not exist").
    @Query("""
            select e from EmailOutbox e
            where (:status is null or e.status = :status)
              and (:recipientEmail is null
                   or lower(e.recipientEmail) like lower(concat('%', cast(:recipientEmail as string), '%')))
            order by e.createdAt desc
            """)
    List<EmailOutbox> search(@Param("status") EmailOutboxStatus status,
                              @Param("recipientEmail") String recipientEmail,
                              Pageable pageable);
}

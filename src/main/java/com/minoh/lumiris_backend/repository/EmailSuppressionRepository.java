package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.EmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, String> {

    boolean existsByEmailIgnoreCase(String email);
}

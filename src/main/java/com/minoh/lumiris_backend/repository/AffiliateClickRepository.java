package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.AffiliateClick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AffiliateClickRepository extends JpaRepository<AffiliateClick, UUID> {
}

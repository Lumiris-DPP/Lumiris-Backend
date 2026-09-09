package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairerProspectOutreach;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RepairerProspectOutreachRepository extends JpaRepository<RepairerProspectOutreach, UUID> {
}

package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppFormDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DppFormDocumentRepository extends JpaRepository<DppFormDocument, UUID> {

    // Bulk delete on purpose: bypasses the persistence context so draft edits can
    // replace children without fighting Hibernate's cascade/remove bookkeeping.
    @Modifying
    @Query("delete from DppFormDocument d where d.dppForm = :form")
    void deleteByDppForm(@Param("form") DppForm form);
}

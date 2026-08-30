package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppFormDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DppFormDocumentRepository extends JpaRepository<DppFormDocument, UUID> {

    // Bulk delete on purpose: bypasses the persistence context so draft edits can
    // replace children without fighting Hibernate's cascade/remove bookkeeping.
    @Modifying
    @Query("delete from DppFormDocument d where d.dppForm = :form")
    void deleteByDppForm(@Param("form") DppForm form);

    // Same rationale, narrowed to one type: re-uploading a document replaces the stored one.
    @Modifying
    @Query("delete from DppFormDocument d where d.dppForm = :form and d.documentType = :type")
    void deleteByDppFormAndDocumentType(@Param("form") DppForm form, @Param("type") DocumentType type);

    interface FileUsageCount {
        UUID getFileId();
        long getCount();
    }

    // "Utilisé sur N passeport(s)" côté bibliothèque de certificats : distinct sur dppForm, un
    // même fichier ne compte qu'une fois par passeport même s'il y figure sous deux DocumentType.
    @Query("select d.file.id as fileId, count(distinct d.dppForm.id) as count "
            + "from DppFormDocument d where d.file.id in :fileIds group by d.file.id")
    List<FileUsageCount> countDistinctDppFormsByFileIds(@Param("fileIds") Collection<UUID> fileIds);
}

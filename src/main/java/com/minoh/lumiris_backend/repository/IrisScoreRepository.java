package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.IrisScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IrisScoreRepository extends JpaRepository<IrisScore, UUID> {

    // Une ligne par grade présent chez l'artisan — le tableau de bord n'a jamais besoin des scores eux-mêmes.
    interface GradeCount {
        String getGrade();
        long getTotal();
    }

    Optional<IrisScore> findByDppFormId(UUID dppFormId);

    List<IrisScore> findByDppFormIdIn(Collection<UUID> dppFormIds);

    @Query("""
            select s.grade as grade, count(s) as total
            from IrisScore s
            where s.dppForm.user.id = :userId and s.dppForm.status <> :excludedStatus
            group by s.grade
            """)
    List<GradeCount> countByGrade(@Param("userId") UUID userId, @Param("excludedStatus") DppStatus excludedStatus);

    @Query("""
            select avg(s.total)
            from IrisScore s
            where s.dppForm.user.id = :userId and s.dppForm.status = :status
            """)
    Double averageTotal(@Param("userId") UUID userId, @Param("status") DppStatus status);
}

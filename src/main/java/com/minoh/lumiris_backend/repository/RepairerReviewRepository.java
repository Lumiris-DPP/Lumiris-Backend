package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RepairerReviewRepository extends JpaRepository<RepairerReview, UUID> {

    List<RepairerReview> findByRepairerProfileOrderByCreatedAtDesc(RepairerProfile repairerProfile);

    boolean existsByRepairRequestId(java.util.UUID repairRequestId);

    @Query("select avg(r.rating) from RepairerReview r where r.repairerProfile = :profile")
    Double averageRating(@Param("profile") RepairerProfile profile);

    long countByRepairerProfile(RepairerProfile repairerProfile);
}

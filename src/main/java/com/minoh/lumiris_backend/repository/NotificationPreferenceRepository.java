package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.NotificationCategory;
import com.minoh.lumiris_backend.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUser_Id(UUID userId);

    Optional<NotificationPreference> findByUser_IdAndCategory(UUID userId, NotificationCategory category);
}

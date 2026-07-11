package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
}

package com.datasage.dashboard.repository;

import com.datasage.dashboard.model.UploadedFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    Optional<UploadedFile> findTopByOrderByUploadedAtDesc();
}

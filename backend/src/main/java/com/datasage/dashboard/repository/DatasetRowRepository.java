package com.datasage.dashboard.repository;

import com.datasage.dashboard.model.DatasetRow;
import com.datasage.dashboard.model.UploadedFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {
    List<DatasetRow> findByUploadedFileOrderByIdAsc(UploadedFile uploadedFile);
}

package com.datasage.dashboard.repository;

import com.datasage.dashboard.model.QueryHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {
    List<QueryHistory> findTop20ByOrderByCreatedAtDesc();
}

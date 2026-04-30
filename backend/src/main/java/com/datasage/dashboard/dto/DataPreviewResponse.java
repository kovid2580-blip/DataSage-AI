package com.datasage.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DataPreviewResponse(
        Long fileId,
        String fileName,
        List<String> headers,
        List<Map<String, String>> rows,
        long totalRows,
        LocalDateTime uploadedAt
) {
}

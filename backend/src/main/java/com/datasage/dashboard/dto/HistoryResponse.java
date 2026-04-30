package com.datasage.dashboard.dto;

import java.time.LocalDateTime;

public record HistoryResponse(
        Long id,
        String question,
        String structuredQueryJson,
        String responseJson,
        LocalDateTime createdAt
) {
}

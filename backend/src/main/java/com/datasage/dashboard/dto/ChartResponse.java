package com.datasage.dashboard.dto;

import java.util.List;

public record ChartResponse(
        String chartType,
        List<String> labels,
        List<Double> values,
        String summary
) {
}

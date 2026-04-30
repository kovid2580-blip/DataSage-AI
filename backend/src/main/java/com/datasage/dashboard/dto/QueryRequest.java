package com.datasage.dashboard.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(@NotBlank String question) {
}

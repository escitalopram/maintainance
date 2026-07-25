package com.maintainance.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PlanRequest(
        @NotNull LocalDate horizonStart,
        @NotNull LocalDate horizonEnd
) {
}

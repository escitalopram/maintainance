package com.maintainance.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompleteInstanceRequest(
        UUID openInstanceId,
        @NotNull LocalDate scheduledAt,
        LocalDate plannedAt,
        Double intervalDeltaPercent
) {
}

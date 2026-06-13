package com.maintainance.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SettingsRequest(
        @Min(1) int softBudgetMinutes,
        @Min(1) int hardCapMinutes,
        @Min(0) double painThreshold,
        @Min(0) double painPerMinuteOverThreshold,
        @Min(0) double beta,
        @Min(0) double defaultBacklogP,
        @Min(1) int planningExtendFactor
) {
}

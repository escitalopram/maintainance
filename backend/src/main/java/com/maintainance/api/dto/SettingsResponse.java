package com.maintainance.api.dto;

public record SettingsResponse(
        int softBudgetMinutes,
        int hardCapMinutes,
        double painThreshold,
        double painPerMinuteOverThreshold,
        double beta,
        double defaultBacklogP,
        int planningExtendFactor
) {
}

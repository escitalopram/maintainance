package com.maintainance.domain.model;

public record PlannerSettings(
        int softBudgetMinutes,
        int hardCapMinutes,
        double painThreshold,
        double painPerMinuteOverThreshold,
        double beta,
        double defaultBacklogP,
        int planningExtendFactor
) {
    public static PlannerSettings defaults() {
        return new PlannerSettings(120, 480, 100, 0.05, 0.5, 0.6, 2);
    }
}

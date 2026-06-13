package com.maintainance.api.dto;

import java.time.LocalDate;

public record PlanDayResponse(
        LocalDate date,
        int loadMinutes,
        boolean overHardCap,
        double dailyPain
) {
}

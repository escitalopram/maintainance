package com.maintainance.domain.planning;

import java.time.LocalDate;

public record PlanDay(
        LocalDate date,
        int loadMinutes,
        boolean overHardCap,
        double dailyPain
) {
}

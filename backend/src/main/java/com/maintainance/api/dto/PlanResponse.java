package com.maintainance.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        LocalDate horizonStart,
        LocalDate horizonEnd,
        double pTotal,
        double pTiming,
        double pTimingUnassigned,
        double pDaily,
        double pStar,
        List<PlanDayResponse> days,
        List<PlanItemResponse> items,
        List<String> warnings
) {
}

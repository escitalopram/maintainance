package com.maintainance.domain.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResult(
        LocalDate horizonStart,
        LocalDate horizonEnd,
        double pTotal,
        double pTiming,
        double pTimingUnassigned,
        double pDaily,
        double pStar,
        List<PlanDay> days,
        List<PlanItem> items,
        List<String> warnings
) {
}

package com.maintainance.api;

import com.maintainance.api.dto.PlanDayResponse;
import com.maintainance.api.dto.PlanItemResponse;
import com.maintainance.api.dto.PlanResponse;
import com.maintainance.domain.planning.PlanDay;
import com.maintainance.domain.planning.PlanItem;
import com.maintainance.domain.planning.PlanResult;
import org.springframework.stereotype.Component;

@Component
public class PlanResponseMapper {

    public PlanResponse toResponse(PlanResult result) {
        return new PlanResponse(
                result.horizonStart(),
                result.horizonEnd(),
                result.pTotal(),
                result.pTiming(),
                result.pTimingUnassigned(),
                result.pDaily(),
                result.pStar(),
                result.days().stream().map(this::toDay).toList(),
                result.items().stream().map(this::toItem).toList(),
                result.warnings()
        );
    }

    private PlanDayResponse toDay(PlanDay day) {
        return new PlanDayResponse(day.date(), day.loadMinutes(), day.overHardCap(), day.dailyPain());
    }

    private PlanItemResponse toItem(PlanItem item) {
        return new PlanItemResponse(
                item.instanceKey(),
                item.taskId(),
                item.scheduledAt(),
                item.plannedAt(),
                item.placement(),
                item.timingPain(),
                item.durationMinutes(),
                item.withinDayOrder(),
                item.virtual(),
                item.backlog(),
                item.overdue()
        );
    }
}

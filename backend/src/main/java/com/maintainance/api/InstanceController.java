package com.maintainance.api;

import com.maintainance.api.dto.CompletionResponse;
import com.maintainance.api.dto.CompleteInstanceRequest;
import com.maintainance.api.dto.SnoozeInstanceRequest;
import com.maintainance.service.PlannerFacade;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InstanceController {

    private final PlannerFacade plannerFacade;

    public InstanceController(PlannerFacade plannerFacade) {
        this.plannerFacade = plannerFacade;
    }

    @GetMapping("/tasks/{taskId}/completions")
    public List<CompletionResponse> completions(@PathVariable UUID taskId) {
        return plannerFacade.listCompletions(taskId).stream()
                .map(entity -> new CompletionResponse(
                        entity.getId(),
                        taskId,
                        entity.getScheduledAt(),
                        entity.getPlannedAt(),
                        entity.getCompletedAt()))
                .toList();
    }

    @PostMapping("/instances/{taskId}/complete")
    public void complete(@PathVariable UUID taskId, @Valid @RequestBody CompleteInstanceRequest request) {
        plannerFacade.completeInstance(
                taskId,
                request.openInstanceId(),
                request.scheduledAt(),
                request.plannedAt(),
                request.intervalDeltaPercent()
        );
    }

    @PostMapping("/instances/{taskId}/snooze")
    public void snooze(@PathVariable UUID taskId, @Valid @RequestBody SnoozeInstanceRequest request) {
        plannerFacade.snoozeInstance(taskId, request.snoozeUntil());
    }
}

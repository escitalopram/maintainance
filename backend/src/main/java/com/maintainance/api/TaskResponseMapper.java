package com.maintainance.api;

import com.maintainance.api.dto.TaskResponse;
import com.maintainance.domain.model.TaskState;
import org.springframework.stereotype.Component;

@Component
public class TaskResponseMapper {

    public TaskResponse toResponse(TaskState state) {
        return new TaskResponse(
                state.id(),
                state.name(),
                state.description(),
                state.archived(),
                state.rules(),
                state.epochStart(),
                state.nextScheduled(),
                state.lastMissedScheduledAt(),
                state.catchUpCount(),
                state.openInstance()
        );
    }
}

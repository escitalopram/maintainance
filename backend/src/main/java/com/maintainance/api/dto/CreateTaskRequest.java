package com.maintainance.api.dto;

import com.maintainance.domain.model.TaskRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotBlank String name,
        String description,
        @NotNull TaskRules rules
) {
}

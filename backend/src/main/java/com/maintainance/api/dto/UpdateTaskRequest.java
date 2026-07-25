package com.maintainance.api.dto;

import com.maintainance.domain.model.TaskRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskRequest(
        @NotBlank String name,
        String description,
        @NotNull TaskRules rules,
        Boolean archived
) {
}

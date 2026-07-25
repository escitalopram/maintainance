package com.maintainance.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SnoozeInstanceRequest(
        @NotNull LocalDate snoozeUntil
) {
}

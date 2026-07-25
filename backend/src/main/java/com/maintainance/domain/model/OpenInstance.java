package com.maintainance.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record OpenInstance(
        UUID id,
        UUID taskId,
        LocalDate scheduledAt,
        LocalDate snoozeUntil
) {
}

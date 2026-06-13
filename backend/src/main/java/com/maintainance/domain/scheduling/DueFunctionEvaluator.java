package com.maintainance.domain.scheduling;

import java.time.LocalDate;

public interface DueFunctionEvaluator {
    boolean isDue(String scriptPath, String scriptArgs);
}

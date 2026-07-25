package com.maintainance.domain.pain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PainCalculatorTest {

    private final PainCalculator calculator = new PainCalculator();

    @Test
    void regimeAZeroInsideGrace() {
        var rules = com.maintainance.domain.model.TaskRules.defaultsDaily();
        rules = new com.maintainance.domain.model.TaskRules(
                rules.intervalType(), rules.intervalN(), rules.anchorMode(), rules.catchUp(),
                rules.useBacklogMultiplier(), rules.allowedWeekdays(), rules.seasonStart(),
                rules.seasonEnd(), rules.endDate(), rules.minDaysBetweenScheduled(),
                rules.durationMinutes(), rules.importanceWeight(), 2, 2,
                rules.sigmaEarly(), rules.sigmaLate(), rules.backlogP(),
                rules.dueScriptPath(), rules.dueScriptArgs()
        );
        LocalDate scheduled = LocalDate.of(2026, 6, 10);
        assertThat(calculator.regimeA(rules, scheduled, scheduled)).isZero();
        assertThat(calculator.regimeA(rules, scheduled, scheduled.plusDays(2))).isZero();
    }

    @Test
    void backlogMultiplierAtOneForSingleCount() {
        assertThat(calculator.backlogMultiplier(1, 0.6, 0.5)).isEqualTo(1.0);
    }
}

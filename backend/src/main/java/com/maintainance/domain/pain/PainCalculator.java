package com.maintainance.domain.pain;

import com.maintainance.domain.model.PlannerSettings;
import com.maintainance.domain.model.SchedulableInstance;
import com.maintainance.domain.model.TaskRules;

import java.time.LocalDate;

public class PainCalculator {

    public double timingPain(
            SchedulableInstance instance,
            LocalDate plannedDate,
            LocalDate horizonStart,
            LocalDate earliestFeasibleDay,
            boolean forceRegimeA
    ) {
        TaskRules rules = instance.rules();
        double base;
        if (forceRegimeA) {
            base = regimeA(rules, instance.scheduledAt(), plannedDate);
        } else if (instance.overdue()) {
            if (plannedDate.equals(earliestFeasibleDay)) {
                base = 0;
            } else {
                base = regimeA(rules, instance.scheduledAt(), plannedDate);
            }
        } else {
            base = regimeA(rules, instance.scheduledAt(), plannedDate);
        }
        return base * backlogMultiplier(instance, rules);
    }

    public double backlogMultiplier(SchedulableInstance instance, TaskRules rules) {
        if (!instance.isBacklogVirtual() || !rules.useBacklogMultiplier()) {
            return 1.0;
        }
        int c = Math.max(1, instance.catchUpCountForTask());
        return backlogMultiplier(c, rules.backlogP(), 0.5); // beta from settings applied externally
    }

    public double backlogMultiplier(int c, double backlogP, double beta) {
        if (c <= 1) {
            return 1.0;
        }
        return 1.0 + beta * Math.pow(c - 1, backlogP);
    }

    public double regimeA(TaskRules rules, LocalDate scheduled, LocalDate planned) {
        long delta = planned.toEpochDay() - scheduled.toEpochDay();
        if (delta >= -rules.graceEarlyDays() && delta <= rules.graceLateDays()) {
            return 0;
        }
        long deltaEff;
        if (delta < -rules.graceEarlyDays()) {
            deltaEff = delta + rules.graceEarlyDays();
        } else {
            deltaEff = delta - rules.graceLateDays();
        }
        double sigma = deltaEff < 0 ? rules.sigmaEarly() : rules.sigmaLate();
        if (sigma <= 0) {
            sigma = 1.0;
        }
        double v = Math.exp(-(deltaEff * deltaEff) / (2.0 * sigma * sigma));
        return rules.importanceWeight() * (1.0 - v);
    }

    public double dailyPain(int loadMinutes, PlannerSettings settings) {
        int t = settings.softBudgetMinutes();
        if (loadMinutes <= t) {
            return linearRho(loadMinutes, t, settings.painThreshold());
        }
        double base = linearRho(t, t, settings.painThreshold());
        return base + settings.painPerMinuteOverThreshold() * (loadMinutes - t);
    }

    private double linearRho(int loadMinutes, int threshold, double painAtThreshold) {
        if (threshold <= 0) {
            return 0;
        }
        return painAtThreshold * loadMinutes / threshold;
    }

    public double marginalDailyPainChange(
            int loadBefore,
            int duration,
            PlannerSettings settings
    ) {
        return dailyPain(loadBefore + duration, settings) - dailyPain(loadBefore, settings);
    }
}

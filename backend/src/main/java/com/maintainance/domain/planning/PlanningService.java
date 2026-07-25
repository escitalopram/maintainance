package com.maintainance.domain.planning;

import com.maintainance.domain.model.PlannerSettings;
import com.maintainance.domain.model.SchedulableInstance;
import com.maintainance.domain.model.TaskRules;
import com.maintainance.domain.pain.PainCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class PlanningService {

    private final PainCalculator painCalculator = new PainCalculator();

    public PlanResult plan(
            List<SchedulableInstance> instances,
            LocalDate userStart,
            LocalDate userEnd,
            LocalDate planEnd,
            PlannerSettings settings
    ) {
        Map<String, LocalDate> assignment = new LinkedHashMap<>();
        Map<LocalDate, Integer> loads = new HashMap<>();
        List<SchedulableInstance> ordered = sortInstances(instances);

        List<SchedulableInstance> overflowPending = new ArrayList<>();
        boolean strictFailed = false;

        for (SchedulableInstance instance : ordered) {
            TreeSet<LocalDate> feasible = feasibleDays(instance, userStart, planEnd);
            if (feasible.isEmpty()) {
                assignment.put(instance.instanceKey(), null);
                continue;
            }
            LocalDate best = null;
            double bestPain = Double.MAX_VALUE;
            for (LocalDate day : feasible) {
                int load = loads.getOrDefault(day, 0);
                if (load + instance.rules().durationMinutes() > settings.hardCapMinutes()) {
                    continue;
                }
                double marginal = marginalPain(instance, day, load, userStart, feasible.first(), settings);
                if (marginal < bestPain || (marginal == bestPain && best != null && day.isBefore(best))) {
                    bestPain = marginal;
                    best = day;
                }
            }
            if (best != null) {
                assignment.put(instance.instanceKey(), best);
                loads.merge(best, instance.rules().durationMinutes(), Integer::sum);
            } else {
                overflowPending.add(instance);
                strictFailed = true;
            }
        }

        List<String> warnings = new ArrayList<>();
        if (strictFailed) {
            warnings.add("plan_underflow_strict_cap");
            for (SchedulableInstance instance : overflowPending) {
                TreeSet<LocalDate> feasible = feasibleDays(instance, userStart, planEnd);
                if (feasible.isEmpty()) {
                    assignment.put(instance.instanceKey(), null);
                    continue;
                }
                LocalDate best = null;
                double bestPain = Double.MAX_VALUE;
                for (LocalDate day : feasible) {
                    int load = loads.getOrDefault(day, 0);
                    int slack = settings.hardCapMinutes() - load;
                    double marginal = marginalPain(instance, day, load, userStart, feasible.first(), settings);
                    double score = marginal - slack * 0.001;
                    if (score < bestPain) {
                        bestPain = score;
                        best = day;
                    }
                }
                if (best != null) {
                    assignment.put(instance.instanceKey(), best);
                    loads.merge(best, instance.rules().durationMinutes(), Integer::sum);
                } else {
                    assignment.put(instance.instanceKey(), null);
                    warnings.add("unassigned_instances");
                }
            }
        }

        return buildResult(instances, assignment, loads, userStart, userEnd, settings, warnings);
    }

    private double marginalPain(
            SchedulableInstance instance,
            LocalDate day,
            int loadBefore,
            LocalDate horizonStart,
            LocalDate d0,
            PlannerSettings settings
    ) {
        double beta = settings.beta();
        double timing = timingForAssignment(instance, day, horizonStart, d0, settings, beta, false);
        double daily = painCalculator.marginalDailyPainChange(
                loadBefore, instance.rules().durationMinutes(), settings);
        return timing + daily;
    }

    private double timingForAssignment(
            SchedulableInstance instance,
            LocalDate day,
            LocalDate horizonStart,
            LocalDate d0,
            PlannerSettings settings,
            double beta,
            boolean forceRegimeA
    ) {
        double base;
        TaskRules rules = instance.rules();
        if (forceRegimeA) {
            base = painCalculator.regimeA(rules, instance.scheduledAt(), day);
        } else if (instance.overdue()) {
            base = day.equals(d0) ? 0 : painCalculator.regimeA(rules, instance.scheduledAt(), day);
        } else {
            base = painCalculator.regimeA(rules, instance.scheduledAt(), day);
        }
        double m = instance.isBacklogVirtual() && rules.useBacklogMultiplier()
                ? painCalculator.backlogMultiplier(instance.catchUpCountForTask(), rules.backlogP(), beta)
                : 1.0;
        return base * m;
    }

    private PlanResult buildResult(
            List<SchedulableInstance> instances,
            Map<String, LocalDate> assignment,
            Map<LocalDate, Integer> loads,
            LocalDate userStart,
            LocalDate userEnd,
            PlannerSettings settings,
            List<String> warnings
    ) {
        LocalDate pBeyond = userEnd.plusDays(1);
        double pTiming = 0;
        double pTimingUnassigned = 0;
        double pDaily = 0;

        List<PlanItem> items = new ArrayList<>();
        for (SchedulableInstance instance : instances) {
            LocalDate internal = assignment.get(instance.instanceKey());
            LocalDate visible = internal != null && !internal.isBefore(userStart) && !internal.isAfter(userEnd)
                    ? internal : null;
            TreeSet<LocalDate> feasibleUser = feasibleDays(instance, userStart, userEnd);
            LocalDate d0 = feasibleUser.isEmpty() ? userStart : feasibleUser.first();

            double timing;
            if (visible != null) {
                timing = timingForAssignment(instance, visible, userStart, d0, settings, settings.beta(), false);
                pTiming += timing;
            } else {
                timing = timingForAssignment(instance, pBeyond, userStart, d0, settings, settings.beta(), true);
                pTimingUnassigned += timing;
            }

            Integer order = visible != null ? 0 : null;
            items.add(new PlanItem(
                    instance.instanceKey(),
                    instance.taskId(),
                    instance.scheduledAt(),
                    visible,
                    visible == null ? "unassigned" : null,
                    timing,
                    instance.rules().durationMinutes(),
                    order,
                    instance.virtual(),
                    instance.backlog(),
                    instance.overdue()
            ));
        }

        List<PlanDay> days = new ArrayList<>();
        for (LocalDate d = userStart; !d.isAfter(userEnd); d = d.plusDays(1)) {
            int load = loads.getOrDefault(d, 0);
            if (load > 0 || loads.containsKey(d)) {
                load = loads.getOrDefault(d, 0);
            }
            // Only count load on user-visible days for reporting
            int userLoad = 0;
            for (Map.Entry<String, LocalDate> e : assignment.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBefore(userStart) && !e.getValue().isAfter(userEnd)
                        && e.getValue().equals(d)) {
                    SchedulableInstance inst = instances.stream()
                            .filter(i -> i.instanceKey().equals(e.getKey()))
                            .findFirst().orElseThrow();
                    userLoad += inst.rules().durationMinutes();
                }
            }
            if (userLoad > 0) {
                days.add(new PlanDay(
                        d,
                        userLoad,
                        userLoad > settings.hardCapMinutes(),
                        painCalculator.dailyPain(userLoad, settings)
                ));
                pDaily += painCalculator.dailyPain(userLoad, settings);
            }
        }

        return new PlanResult(
                userStart,
                userEnd,
                pTiming + pDaily + pTimingUnassigned,
                pTiming,
                pTimingUnassigned,
                pDaily,
                settings.painThreshold(),
                days,
                items,
                warnings
        );
    }

    private TreeSet<LocalDate> feasibleDays(SchedulableInstance instance, LocalDate start, LocalDate end) {
        TreeSet<LocalDate> days = new TreeSet<>();
        LocalDate snooze = instance.snoozeUntil();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (snooze == null || !d.isBefore(snooze)) {
                days.add(d);
            }
        }
        return days;
    }

    private List<SchedulableInstance> sortInstances(List<SchedulableInstance> instances) {
        return instances.stream()
                .sorted(Comparator
                        .comparing((SchedulableInstance i) -> !(i.backlog() || i.overdue()))
                        .thenComparing(i -> -i.rules().importanceWeight())
                        .thenComparing(SchedulableInstance::scheduledAt)
                        .thenComparing(i -> i.taskId().toString()))
                .toList();
    }

    public LocalDate extendedPlanEnd(LocalDate userStart, LocalDate userEnd, int extendFactor) {
        long len = ChronoUnit.DAYS.between(userStart, userEnd) + 1;
        return userEnd.plusDays(len * Math.max(0, extendFactor - 1));
    }
}

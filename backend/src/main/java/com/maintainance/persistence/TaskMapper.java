package com.maintainance.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maintainance.domain.model.AnchorMode;
import com.maintainance.domain.model.IntervalType;
import com.maintainance.domain.model.OpenInstance;
import com.maintainance.domain.model.PlannerSettings;
import com.maintainance.domain.model.TaskRules;
import com.maintainance.domain.model.TaskState;
import com.maintainance.persistence.entity.OpenInstanceEntity;
import com.maintainance.persistence.entity.SettingsEntity;
import com.maintainance.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class TaskMapper {

    private final ObjectMapper objectMapper;

    public TaskMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskState toState(TaskEntity entity, OpenInstanceEntity openEntity) {
        OpenInstance open = openEntity == null ? null : new OpenInstance(
                openEntity.getId(),
                entity.getId(),
                openEntity.getScheduledAt(),
                openEntity.getSnoozeUntil()
        );
        return new TaskState(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isArchived(),
                parseRules(entity.getRulesJson()),
                entity.getEpochStart(),
                entity.getNextScheduled(),
                entity.getLastMissedScheduledAt(),
                entity.getCatchUpCount(),
                open
        );
    }

    public void applyState(TaskEntity entity, TaskState state) {
        entity.setEpochStart(state.epochStart());
        entity.setNextScheduled(state.nextScheduled());
        entity.setLastMissedScheduledAt(state.lastMissedScheduledAt());
        entity.setCatchUpCount(state.catchUpCount());
        entity.setArchived(state.archived());
    }

    public TaskRules parseRules(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return new TaskRules(
                    IntervalType.valueOf((String) map.getOrDefault("intervalType", "EVERY_N_DAYS")),
                    toDouble(map.getOrDefault("intervalN", 1.0)),
                    AnchorMode.valueOf((String) map.getOrDefault("anchorMode", "EPOCH")),
                    toBoolean(map.getOrDefault("catchUp", true)),
                    toBoolean(map.getOrDefault("useBacklogMultiplier", true)),
                    parseWeekdays(map.get("allowedWeekdays")),
                    parseDate(map.get("seasonStart")),
                    parseDate(map.get("seasonEnd")),
                    parseDate(map.get("endDate")),
                    map.get("minDaysBetweenScheduled") == null ? null : toInt(map.get("minDaysBetweenScheduled")),
                    toInt(map.getOrDefault("durationMinutes", 15)),
                    toDouble(map.getOrDefault("importanceWeight", 1.0)),
                    toInt(map.getOrDefault("graceEarlyDays", 0)),
                    toInt(map.getOrDefault("graceLateDays", 0)),
                    toDouble(map.getOrDefault("sigmaEarly", 3.0)),
                    toDouble(map.getOrDefault("sigmaLate", 3.0)),
                    toDouble(map.getOrDefault("backlogP", 0.6)),
                    (String) map.get("dueScriptPath"),
                    (String) map.get("dueScriptArgs")
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid rules JSON", e);
        }
    }

    public String writeRules(TaskRules rules) {
        try {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("intervalType", rules.intervalType().name());
            map.put("intervalN", rules.intervalN());
            map.put("anchorMode", rules.anchorMode().name());
            map.put("catchUp", rules.catchUp());
            map.put("useBacklogMultiplier", rules.useBacklogMultiplier());
            map.put("allowedWeekdays", rules.allowedWeekdays());
            map.put("seasonStart", rules.seasonStart());
            map.put("seasonEnd", rules.seasonEnd());
            map.put("endDate", rules.endDate());
            map.put("minDaysBetweenScheduled", rules.minDaysBetweenScheduled());
            map.put("durationMinutes", rules.durationMinutes());
            map.put("importanceWeight", rules.importanceWeight());
            map.put("graceEarlyDays", rules.graceEarlyDays());
            map.put("graceLateDays", rules.graceLateDays());
            map.put("sigmaEarly", rules.sigmaEarly());
            map.put("sigmaLate", rules.sigmaLate());
            map.put("backlogP", rules.backlogP());
            map.put("dueScriptPath", rules.dueScriptPath());
            map.put("dueScriptArgs", rules.dueScriptArgs());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public PlannerSettings toPlannerSettings(SettingsEntity entity) {
        return new PlannerSettings(
                entity.getSoftBudgetMinutes(),
                entity.getHardCapMinutes(),
                entity.getPainThreshold(),
                entity.getPainPerMinuteOverThreshold(),
                entity.getBeta(),
                entity.getDefaultBacklogP(),
                entity.getPlanningExtendFactor()
        );
    }

    private Set<Integer> parseWeekdays(Object value) {
        if (value == null) {
            return Set.of();
        }
        Set<Integer> out = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object o : iterable) {
                out.add(toInt(o));
            }
        }
        return out;
    }

    private LocalDate parseDate(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return LocalDate.parse(value.toString());
    }

    private boolean toBoolean(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}

package com.maintainance.service;

import com.maintainance.domain.model.OpenInstance;
import com.maintainance.domain.model.PlannerSettings;
import com.maintainance.domain.model.SchedulableInstance;
import com.maintainance.domain.model.TaskRules;
import com.maintainance.domain.model.TaskState;
import com.maintainance.domain.planning.PlanResult;
import com.maintainance.domain.planning.PlanningService;
import com.maintainance.domain.scheduling.IntervalGrid;
import com.maintainance.domain.scheduling.SchedulingService;
import com.maintainance.persistence.CompletionRepository;
import com.maintainance.persistence.OpenInstanceRepository;
import com.maintainance.persistence.SettingsRepository;
import com.maintainance.persistence.TaskMapper;
import com.maintainance.persistence.TaskRepository;
import com.maintainance.persistence.entity.CompletionEntity;
import com.maintainance.persistence.entity.OpenInstanceEntity;
import com.maintainance.persistence.entity.SettingsEntity;
import com.maintainance.persistence.entity.TaskEntity;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PlannerFacade {

    private final TaskRepository taskRepository;
    private final OpenInstanceRepository openInstanceRepository;
    private final CompletionRepository completionRepository;
    private final SettingsRepository settingsRepository;
    private final TaskMapper taskMapper;
    private final SchedulingService schedulingService;
    private final PlanningService planningService;

    public PlannerFacade(
            TaskRepository taskRepository,
            OpenInstanceRepository openInstanceRepository,
            CompletionRepository completionRepository,
            SettingsRepository settingsRepository,
            TaskMapper taskMapper,
            SchedulingService schedulingService
    ) {
        this.taskRepository = taskRepository;
        this.openInstanceRepository = openInstanceRepository;
        this.completionRepository = completionRepository;
        this.settingsRepository = settingsRepository;
        this.taskMapper = taskMapper;
        this.schedulingService = schedulingService;
        this.planningService = new PlanningService();
    }

    @Transactional
    public PlanResult plan(LocalDate horizonStart, LocalDate horizonEnd) {
        LocalDate today = LocalDate.now();
        PlannerSettings settings = loadSettings();
        LocalDate planEnd = planningService.extendedPlanEnd(
                horizonStart, horizonEnd, settings.planningExtendFactor());

        List<SchedulableInstance> allInstances = new ArrayList<>();
        for (TaskEntity entity : taskRepository.findAll()) {
            if (entity.isArchived() && entity.getCatchUpCount() == 0) {
                continue;
            }
            TaskState state = loadAndReconcile(entity, today, horizonStart);
            allInstances.addAll(schedulingService.scheduleHorizon(
                    state, horizonStart, planEnd, today, Set.of()));
        }
        return planningService.plan(allInstances, horizonStart, horizonEnd, planEnd, settings);
    }

    @Transactional
    public TaskState createTask(String name, String description, TaskRules rules) {
        TaskEntity entity = new TaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setDescription(description);
        entity.setArchived(false);
        entity.setRulesJson(taskMapper.writeRules(rules));
        entity.setCatchUpCount(0);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskRepository.save(entity);

        TaskState state = taskMapper.toState(entity, null);
        state = schedulingService.initializeEpoch(state, LocalDate.now());
        state = schedulingService.reconcileOnRead(state, LocalDate.now(), Set.of());
        state = schedulingService.ensureNextScheduled(state, LocalDate.now());
        persistState(entity, state);
        return loadAndReconcile(entity, LocalDate.now(), LocalDate.now());
    }

    @Transactional
    public TaskState updateTask(UUID id, String name, String description, TaskRules rules, Boolean archived) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        entity.setName(name);
        entity.setDescription(description);
        entity.setRulesJson(taskMapper.writeRules(rules));
        if (archived != null) {
            entity.setArchived(archived);
        }
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);
        return loadAndReconcile(entity, LocalDate.now(), LocalDate.now());
    }

    @Transactional
    public void deleteTask(UUID id) {
        openInstanceRepository.deleteByTaskId(id);
        taskRepository.deleteById(id);
    }

    public List<TaskState> listTasks() {
        List<TaskState> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (TaskEntity entity : taskRepository.findAll()) {
            out.add(loadAndReconcile(entity, today, today));
        }
        return out;
    }

    public TaskState getTask(UUID id) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        return loadAndReconcile(entity, LocalDate.now(), LocalDate.now());
    }

    @Transactional
    public void completeInstance(
            UUID taskId,
            UUID openInstanceId,
            LocalDate scheduledAt,
            LocalDate plannedAt,
            Double intervalDeltaPercent
    ) {
        TaskEntity entity = taskRepository.findById(taskId).orElseThrow();
        TaskState state = loadAndReconcile(entity, LocalDate.now(), LocalDate.now());

        CompletionEntity completion = new CompletionEntity();
        completion.setId(UUID.randomUUID());
        completion.setTask(taskRepository.getReferenceById(taskId));
        completion.setScheduledAt(scheduledAt);
        completion.setPlannedAt(plannedAt);
        completion.setCompletedAt(Instant.now());
        completionRepository.save(completion);

        TaskRules rules = state.rules();
        if (intervalDeltaPercent != null && intervalDeltaPercent != 0) {
            double factor = 1.0 + intervalDeltaPercent / 100.0;
            double newN = Math.max(1.0, rules.intervalN() * factor);
            rules = new TaskRules(
                    rules.intervalType(), newN, rules.anchorMode(), rules.catchUp(),
                    rules.useBacklogMultiplier(), rules.allowedWeekdays(), rules.seasonStart(),
                    rules.seasonEnd(), rules.endDate(), rules.minDaysBetweenScheduled(),
                    rules.durationMinutes(), rules.importanceWeight(), rules.graceEarlyDays(),
                    rules.graceLateDays(), rules.sigmaEarly(), rules.sigmaLate(), rules.backlogP(),
                    rules.dueScriptPath(), rules.dueScriptArgs()
            );
            entity.setRulesJson(taskMapper.writeRules(rules));
            state = new TaskState(state.id(), state.name(), state.description(), state.archived(), rules,
                    state.epochStart(), state.nextScheduled(), state.lastMissedScheduledAt(),
                    state.catchUpCount(), state.openInstance());
        }

        if (rules.catchUp()) {
            int count = Math.max(0, state.catchUpCount() - 1);
            LocalDate lastMissed = count == 0 ? null : state.lastMissedScheduledAt();
            state = state.withSchedulingFields(state.epochStart(), state.nextScheduled(), lastMissed, count, null);
            openInstanceRepository.deleteByTaskId(taskId);
        } else {
            openInstanceRepository.deleteByTaskId(taskId);
            state = state.withSchedulingFields(state.epochStart(), null, null, 0, null);
        }

        LocalDate next = schedulingService.computeNextScheduled(state, LocalDate.now());
        if (intervalDeltaPercent != null && intervalDeltaPercent != 0) {
            state = state.withSchedulingFields(next, next, state.lastMissedScheduledAt(), state.catchUpCount(), null);
        } else {
            state = state.withSchedulingFields(state.epochStart(), next, state.lastMissedScheduledAt(),
                    state.catchUpCount(), null);
        }
        state = schedulingService.reconcileOnRead(state, LocalDate.now(), Set.of());
        persistState(entity, state);
    }

    @Transactional
    public void snoozeInstance(UUID taskId, LocalDate snoozeUntil) {
        TaskEntity entity = taskRepository.findById(taskId).orElseThrow();
        TaskState state = loadAndReconcile(entity, LocalDate.now(), LocalDate.now());
        OpenInstance open = state.openInstance();
        if (open == null) {
            throw new IllegalStateException("No open instance to snooze");
        }
        OpenInstanceEntity openEntity = openInstanceRepository.findByTaskId(taskId).orElseThrow();
        openEntity.setSnoozeUntil(snoozeUntil);
        openInstanceRepository.save(openEntity);
    }

    public PlannerSettings getSettings() {
        return loadSettings();
    }

    @Transactional
    public PlannerSettings updateSettings(PlannerSettings settings) {
        SettingsEntity entity = settingsRepository.findById(1L).orElseThrow();
        entity.setSoftBudgetMinutes(settings.softBudgetMinutes());
        entity.setHardCapMinutes(settings.hardCapMinutes());
        entity.setPainThreshold(settings.painThreshold());
        entity.setPainPerMinuteOverThreshold(settings.painPerMinuteOverThreshold());
        entity.setBeta(settings.beta());
        entity.setDefaultBacklogP(settings.defaultBacklogP());
        entity.setPlanningExtendFactor(settings.planningExtendFactor());
        settingsRepository.save(entity);
        return taskMapper.toPlannerSettings(entity);
    }

    public List<CompletionEntity> listCompletions(UUID taskId) {
        return completionRepository.findByTaskIdOrderByCompletedAtDesc(taskId);
    }

    private PlannerSettings loadSettings() {
        return taskMapper.toPlannerSettings(settingsRepository.findById(1L).orElseThrow());
    }

    private TaskState loadAndReconcile(TaskEntity entity, LocalDate today, LocalDate horizonStart) {
        OpenInstanceEntity openEntity = openInstanceRepository.findByTaskId(entity.getId()).orElse(null);
        TaskState state = taskMapper.toState(entity, openEntity);
        if (state.epochStart() == null && !state.rules().isExternalDue()) {
            state = schedulingService.initializeEpoch(state, today);
        }
        Set<LocalDate> assumed = new HashSet<>();
        state = schedulingService.reconcileOnRead(state, today, assumed);
        state = schedulingService.ensureNextScheduled(state, today);
        persistState(entity, state);
        return state;
    }

    private void persistState(TaskEntity entity, TaskState state) {
        taskMapper.applyState(entity, state);
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);

        openInstanceRepository.deleteByTaskId(entity.getId());
        if (state.openInstance() != null) {
            OpenInstance open = state.openInstance();
            OpenInstanceEntity openEntity = new OpenInstanceEntity();
            openEntity.setId(open.id());
            openEntity.setTask(entity);
            openEntity.setScheduledAt(open.scheduledAt());
            openEntity.setSnoozeUntil(open.snoozeUntil());
            openEntity.setCreatedAt(Instant.now());
            openInstanceRepository.save(openEntity);
        }
    }
}

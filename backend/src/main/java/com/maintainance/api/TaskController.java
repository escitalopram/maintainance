package com.maintainance.api;

import com.maintainance.api.dto.CreateTaskRequest;
import com.maintainance.api.dto.TaskResponse;
import com.maintainance.api.dto.UpdateTaskRequest;
import com.maintainance.domain.model.TaskState;
import com.maintainance.service.PlannerFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final PlannerFacade plannerFacade;
    private final TaskResponseMapper mapper;

    public TaskController(PlannerFacade plannerFacade, TaskResponseMapper mapper) {
        this.plannerFacade = plannerFacade;
        this.mapper = mapper;
    }

    @GetMapping
    public List<TaskResponse> list() {
        return plannerFacade.listTasks().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable UUID id) {
        return mapper.toResponse(plannerFacade.getTask(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        TaskState state = plannerFacade.createTask(request.name(), request.description(), request.rules());
        return mapper.toResponse(state);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        TaskState state = plannerFacade.updateTask(
                id, request.name(), request.description(), request.rules(), request.archived());
        return mapper.toResponse(state);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        plannerFacade.deleteTask(id);
    }
}

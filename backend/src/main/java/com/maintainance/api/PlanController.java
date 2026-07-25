package com.maintainance.api;

import com.maintainance.api.dto.PlanRequest;
import com.maintainance.api.dto.PlanResponse;
import com.maintainance.domain.planning.PlanResult;
import com.maintainance.service.PlannerFacade;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlannerFacade plannerFacade;
    private final PlanResponseMapper mapper;

    public PlanController(PlannerFacade plannerFacade, PlanResponseMapper mapper) {
        this.plannerFacade = plannerFacade;
        this.mapper = mapper;
    }

    @PostMapping
    public PlanResponse plan(@Valid @RequestBody PlanRequest request) {
        PlanResult result = plannerFacade.plan(request.horizonStart(), request.horizonEnd());
        return mapper.toResponse(result);
    }
}

package com.maintainance.api;

import com.maintainance.api.dto.SettingsRequest;
import com.maintainance.api.dto.SettingsResponse;
import com.maintainance.domain.model.PlannerSettings;
import com.maintainance.service.PlannerFacade;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final PlannerFacade plannerFacade;

    public SettingsController(PlannerFacade plannerFacade) {
        this.plannerFacade = plannerFacade;
    }

    @GetMapping
    public SettingsResponse get() {
        return toResponse(plannerFacade.getSettings());
    }

    @PutMapping
    public SettingsResponse put(@Valid @RequestBody SettingsRequest request) {
        PlannerSettings updated = plannerFacade.updateSettings(new PlannerSettings(
                request.softBudgetMinutes(),
                request.hardCapMinutes(),
                request.painThreshold(),
                request.painPerMinuteOverThreshold(),
                request.beta(),
                request.defaultBacklogP(),
                request.planningExtendFactor()
        ));
        return toResponse(updated);
    }

    private SettingsResponse toResponse(PlannerSettings settings) {
        return new SettingsResponse(
                settings.softBudgetMinutes(),
                settings.hardCapMinutes(),
                settings.painThreshold(),
                settings.painPerMinuteOverThreshold(),
                settings.beta(),
                settings.defaultBacklogP(),
                settings.planningExtendFactor()
        );
    }
}

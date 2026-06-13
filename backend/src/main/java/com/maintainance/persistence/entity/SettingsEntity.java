package com.maintainance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settings")
public class SettingsEntity {

    @Id
    private Long id = 1L;

    @Column(name = "soft_budget_minutes", nullable = false)
    private int softBudgetMinutes;

    @Column(name = "hard_cap_minutes", nullable = false)
    private int hardCapMinutes;

    @Column(name = "pain_threshold", nullable = false)
    private double painThreshold;

    @Column(name = "pain_per_minute_over_threshold", nullable = false)
    private double painPerMinuteOverThreshold;

    @Column(nullable = false)
    private double beta;

    @Column(name = "default_backlog_p", nullable = false)
    private double defaultBacklogP;

    @Column(name = "planning_extend_factor", nullable = false)
    private int planningExtendFactor;

    public SettingsEntity() {
    }

    public Long getId() {
        return id;
    }

    public int getSoftBudgetMinutes() {
        return softBudgetMinutes;
    }

    public void setSoftBudgetMinutes(int softBudgetMinutes) {
        this.softBudgetMinutes = softBudgetMinutes;
    }

    public int getHardCapMinutes() {
        return hardCapMinutes;
    }

    public void setHardCapMinutes(int hardCapMinutes) {
        this.hardCapMinutes = hardCapMinutes;
    }

    public double getPainThreshold() {
        return painThreshold;
    }

    public void setPainThreshold(double painThreshold) {
        this.painThreshold = painThreshold;
    }

    public double getPainPerMinuteOverThreshold() {
        return painPerMinuteOverThreshold;
    }

    public void setPainPerMinuteOverThreshold(double painPerMinuteOverThreshold) {
        this.painPerMinuteOverThreshold = painPerMinuteOverThreshold;
    }

    public double getBeta() {
        return beta;
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }

    public double getDefaultBacklogP() {
        return defaultBacklogP;
    }

    public void setDefaultBacklogP(double defaultBacklogP) {
        this.defaultBacklogP = defaultBacklogP;
    }

    public int getPlanningExtendFactor() {
        return planningExtendFactor;
    }

    public void setPlanningExtendFactor(int planningExtendFactor) {
        this.planningExtendFactor = planningExtendFactor;
    }
}

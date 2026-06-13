package com.maintainance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "task")
public class TaskEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "rules_json", nullable = false, columnDefinition = "CLOB")
    private String rulesJson;

    @Column(name = "epoch_start")
    private LocalDate epochStart;

    @Column(name = "next_scheduled")
    private LocalDate nextScheduled;

    @Column(name = "last_missed_scheduled_at")
    private LocalDate lastMissedScheduledAt;

    @Column(name = "catch_up_count", nullable = false)
    private int catchUpCount;

    @Column(name = "last_reconciled_date", nullable = false)
    private LocalDate lastReconciledDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TaskEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getRulesJson() {
        return rulesJson;
    }

    public void setRulesJson(String rulesJson) {
        this.rulesJson = rulesJson;
    }

    public LocalDate getEpochStart() {
        return epochStart;
    }

    public void setEpochStart(LocalDate epochStart) {
        this.epochStart = epochStart;
    }

    public LocalDate getNextScheduled() {
        return nextScheduled;
    }

    public void setNextScheduled(LocalDate nextScheduled) {
        this.nextScheduled = nextScheduled;
    }

    public LocalDate getLastMissedScheduledAt() {
        return lastMissedScheduledAt;
    }

    public void setLastMissedScheduledAt(LocalDate lastMissedScheduledAt) {
        this.lastMissedScheduledAt = lastMissedScheduledAt;
    }

    public int getCatchUpCount() {
        return catchUpCount;
    }

    public void setCatchUpCount(int catchUpCount) {
        this.catchUpCount = catchUpCount;
    }

    public LocalDate getLastReconciledDate() {
        return lastReconciledDate;
    }

    public void setLastReconciledDate(LocalDate lastReconciledDate) {
        this.lastReconciledDate = lastReconciledDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

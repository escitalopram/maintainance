package com.maintainance.persistence;

import com.maintainance.persistence.entity.CompletionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompletionRepository extends JpaRepository<CompletionEntity, UUID> {
    List<CompletionEntity> findByTaskIdOrderByCompletedAtDesc(UUID taskId);
}

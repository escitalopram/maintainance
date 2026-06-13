package com.maintainance.persistence;

import com.maintainance.persistence.entity.OpenInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OpenInstanceRepository extends JpaRepository<OpenInstanceEntity, UUID> {
    Optional<OpenInstanceEntity> findByTaskId(UUID taskId);

    void deleteByTaskId(UUID taskId);
}

package com.maintainance.persistence;

import com.maintainance.persistence.entity.SettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<SettingsEntity, Long> {
}

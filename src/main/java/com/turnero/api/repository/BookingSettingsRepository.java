package com.turnero.api.repository;

import com.turnero.api.model.BookingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingSettingsRepository extends JpaRepository<BookingSettings, Long> {
    Optional<BookingSettings> findByBusinessId(Long businessId);
}

package com.turnero.api.repository;

import com.turnero.api.model.BookingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSettingsRepository extends JpaRepository<BookingSettings, Long> {
}

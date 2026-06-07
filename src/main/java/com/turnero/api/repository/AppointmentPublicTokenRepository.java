package com.turnero.api.repository;

import com.turnero.api.model.AppointmentPublicToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentPublicTokenRepository extends JpaRepository<AppointmentPublicToken, Long> {
}

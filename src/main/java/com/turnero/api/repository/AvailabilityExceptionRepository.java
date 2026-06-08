package com.turnero.api.repository;

import com.turnero.api.model.AvailabilityException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, Long> {
}

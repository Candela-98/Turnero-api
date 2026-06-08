package com.turnero.api.repository;

import com.turnero.api.model.StaffWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffWorkingHoursRepository extends JpaRepository<StaffWorkingHours, Long> {
}

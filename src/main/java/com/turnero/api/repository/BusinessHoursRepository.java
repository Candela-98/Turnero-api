package com.turnero.api.repository;

import com.turnero.api.model.BusinessHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessHoursRepository extends JpaRepository<BusinessHours, Long> {
    List<BusinessHours> findAllByBusinessId(Long businessId);
}

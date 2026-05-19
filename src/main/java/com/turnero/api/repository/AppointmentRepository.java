package com.turnero.api.repository;

import com.turnero.api.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByStaffMemberId(Long staffMemberId);
}

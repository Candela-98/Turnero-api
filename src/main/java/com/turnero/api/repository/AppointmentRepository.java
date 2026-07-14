package com.turnero.api.repository;

import com.turnero.api.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findAllByBusinessId(Long businessId);

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    List<Appointment> findByStaffMemberIdAndBusinessId(Long staffMemberId, Long businessId);
}

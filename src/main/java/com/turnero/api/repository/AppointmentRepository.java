package com.turnero.api.repository;

import com.turnero.api.model.Appointment;
import com.turnero.api.model.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findAllByBusinessId(Long businessId);

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    List<Appointment> findByStaffMemberIdAndBusinessId(Long staffMemberId, Long businessId);

    boolean existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
            Long businessId,
            Long staffMemberId,
            Collection<AppointmentStatus> statuses,
            LocalDateTime startsAt
    );

    @Query("""
            SELECT appointment
            FROM Appointment appointment
            WHERE appointment.businessId = :businessId
              AND appointment.staffMemberId = :staffMemberId
              AND appointment.status IN :statuses
              AND appointment.startsAt < :rangeEnd
              AND appointment.endsAt > :rangeStart
            """)
    List<Appointment> findBlockingAppointments(
            @Param("businessId") Long businessId,
            @Param("staffMemberId") Long staffMemberId,
            @Param("statuses") List<AppointmentStatus> blockingStatuses,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );
}

package com.turnero.api.service;

import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Appointment;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServOfferingRepository serviceRepository;
    private final StaffMemberRepository staffMemberRepository;


    private void validateReferences(Appointment appointment) {
        if (!customerRepository.existsById(appointment.getCustomerId())) {
            throw new ResourceNotFoundException("Customer not found.");
        }
        if (!serviceRepository.existsById(appointment.getServiceId())) {
            throw new ResourceNotFoundException("Service offering not found.");
        }
        if (!staffMemberRepository.existsById(appointment.getStaffMemberId())) {
            throw new ResourceNotFoundException("Staff member not found.");
        }
    }

    private void validateNoOverlap(Long staffMemberId, LocalDateTime newStart,int durationMinutes,
                                   Long appointmentIdToExclude) {

        LocalDateTime newEnd = newStart.plusMinutes(durationMinutes);
        List<Appointment> staffAppointments = appointmentRepository.findByStaffMemberId(staffMemberId);
        boolean hasOverlap = staffAppointments.stream()
                .filter(existing -> appointmentIdToExclude == null
                        || !existing.getId().equals(appointmentIdToExclude))
                .anyMatch(existing -> {
                    LocalDateTime existingStart = existing.getDateTime();
                    LocalDateTime existingEnd = existingStart.plusMinutes(existing.getDurationMinutes());
                    return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
                });

        if(hasOverlap) {
            throw new AppointmentOverlapException("Staff member already has an appointment in this time range");
        }
    }

    @Override
    public void saveAppointment(Appointment appointment) {
        validateReferences(appointment);

        validateNoOverlap(
                appointment.getStaffMemberId(),
                appointment.getDateTime(),
                appointment.getDurationMinutes(),
                null
        );

        LocalDateTime now = LocalDateTime.now();
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        log.info("Appointment created with id={}", savedAppointment.getId());
    }

    @Override
    public List<Appointment> findAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Override
    public Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with ID: " + id));
    }

    @Override
    public void updateAppointment(Appointment appointment, Long id) {
        Appointment existAppointment = findAppointment(id);

        validateReferences(appointment);

        validateNoOverlap(
                appointment.getStaffMemberId(),
                appointment.getDateTime(),
                appointment.getDurationMinutes(),
                id
        );

        existAppointment.setCustomerId(appointment.getCustomerId());
        existAppointment.setServiceId(appointment.getServiceId());
        existAppointment.setStaffMemberId(appointment.getStaffMemberId());
        existAppointment.setDateTime(appointment.getDateTime());
        existAppointment.setDurationMinutes(appointment.getDurationMinutes());
        existAppointment.setStatus(appointment.getStatus());
        existAppointment.setNotes(appointment.getNotes());
        existAppointment.setUpdatedAt(LocalDateTime.now());

        appointmentRepository.save(existAppointment);
        log.info("Appointment with id={} successfully updated.", id);
    }

    @Override
    public void deleteAppointment(Long id) {
        if(appointmentRepository.existsById(id)) {
            appointmentRepository.deleteById(id);
            log.info("Appointment with id={} successfully deleted.", id);
        } else {
            throw new ResourceNotFoundException("Appointment not found with ID: " + id);
        }
    }
}

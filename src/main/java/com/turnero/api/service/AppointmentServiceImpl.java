package com.turnero.api.service;

import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Appointment;
import com.turnero.api.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public void saveAppointment(Appointment appointment) {

        LocalDateTime now = LocalDateTime.now();
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);

        appointmentRepository.save(appointment);
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

        existAppointment.setCustomerId(appointment.getCustomerId());
        existAppointment.setServiceId(appointment.getServiceId());
        existAppointment.setStaffMemberId(appointment.getStaffMemberId());
        existAppointment.setDateTime(appointment.getDateTime());
        existAppointment.setDurationMinutes(appointment.getDurationMinutes());
        existAppointment.setStatus(appointment.getStatus());
        existAppointment.setNotes(appointment.getNotes());
        existAppointment.setUpdatedAt(LocalDateTime.now());

        appointmentRepository.save(existAppointment);
        System.out.println("Appointment with ID " + id + " successfully updated.");
    }

    @Override
    public void deleteAppointment(Long id) {
        if(appointmentRepository.existsById(id)) {
            appointmentRepository.deleteById(id);
            System.out.println("Appointment with ID " + id + " successfully deleted.");
        } else {
            throw new ResourceNotFoundException("Appointment not found with ID: " + id);
        }
    }
}

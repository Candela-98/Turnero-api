package com.turnero.api.controller;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentMapper appointmentMapper;

    public AppointmentController(AppointmentService appointmentService, AppointmentMapper appointmentMapper) {
        this.appointmentService = appointmentService;
        this.appointmentMapper = appointmentMapper;
    }

    @PostMapping
    public void saveAppointment(@Valid @RequestBody AppointmentRequestDto appointmentDto) {

        var appointment = appointmentMapper.toEntity(appointmentDto);
        appointmentService.saveAppointment(appointment);
    }

    @GetMapping
    public List<Appointment> findAllAppointment() {
        return appointmentService.findAllAppointments();
    }

    @GetMapping("/{id}")
    public Appointment findAppointment(@PathVariable Long id) {
        return appointmentService.findAppointment(id);
    }

    @PutMapping("/{id}")
    public void updateAppointment(@Valid @RequestBody AppointmentRequestDto appointmentDto, @PathVariable Long id) {
        var appointment = appointmentMapper.toEntity(appointmentDto);
        appointmentService.updateAppointment(appointment, id);
    }

    @DeleteMapping("/{id}")
    public void deleteAppointment(@PathVariable Long id){
        appointmentService.deleteAppointment(id);
    }

}

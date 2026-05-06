package com.turnero.api.controller;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AppointmentResponseDto> saveAppointment(@Valid @RequestBody AppointmentRequestDto appointmentDto) {
        var appointment = appointmentMapper.toEntity(appointmentDto);
        appointmentService.saveAppointment(appointment);
        var response = appointmentMapper.toResponseDto(appointment);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponseDto>> findAllAppointment() {
        var appointments = appointmentService.findAllAppointments();
        var response = appointmentMapper.toResponseDtoList(appointments);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDto> findAppointment(@PathVariable Long id) {
        var appointment = appointmentService.findAppointment(id);
        var response = appointmentMapper.toResponseDto(appointment);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Appointment> updateAppointment(@Valid @RequestBody AppointmentRequestDto appointmentDto, @PathVariable Long id) {
        var appointment = appointmentMapper.toEntity(appointmentDto);
        appointmentService.updateAppointment(appointment, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Appointment> deleteAppointment(@PathVariable Long id){
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }

}

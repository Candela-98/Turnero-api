package com.turnero.api.controller;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.dto.AppointmentUpdateRequestDto;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.openapi.*;
import com.turnero.api.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(
        name = "Appointments",
        description = "Endpoints para gestionar turnos"
)
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentMapper appointmentMapper;

    public AppointmentController(AppointmentService appointmentService, AppointmentMapper appointmentMapper) {
        this.appointmentService = appointmentService;
        this.appointmentMapper = appointmentMapper;
    }

    @Operation(
            summary = "Crear turno",
            description = "Crea un nuevo turno en el sistema"
    )
    @ApiCreateResponses
    @PostMapping
    public ResponseEntity<AppointmentResponseDto> saveAppointment(@Valid @RequestBody AppointmentRequestDto request) {
        AppointmentResponseDto response = appointmentService.saveAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Obtener todos los turnos",
            description = "Obtiene una lista de todos los turnos registrados en el sistema"
    )
    @ApiFindAllResponses
    @GetMapping
    public ResponseEntity<List<AppointmentResponseDto>> findAllAppointment() {
        var appointments = appointmentService.findAllAppointments();
        var response = appointmentMapper.toResponseDtoList(appointments);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Obtener turno por ID",
            description = "Obtiene los detalles de un turno específico utilizando su ID"
    )
    @ApiFindByIdResponses
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDto> findAppointment(@PathVariable Long id) {
        var appointment = appointmentService.findAppointment(id);
        var response = appointmentMapper.toResponseDto(appointment);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Actualizar turno",
            description = "Actualiza parcialmente los datos permitidos de un turno específico"
    )
    @ApiUpdateResponses
    @PatchMapping("/{id}")
    public ResponseEntity<AppointmentResponseDto> updateAppointment(
            @PathVariable Long id, @Valid @RequestBody AppointmentUpdateRequestDto request) {

        AppointmentResponseDto response =
                appointmentService.updateAppointment(id, request);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Eliminar turno",
            description = "Elimina un turno específico utilizando su ID"
    )
    @ApiDeleteResponses
    @DeleteMapping("/{id}")
    public ResponseEntity<Appointment> deleteAppointment(@PathVariable Long id){
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }

}

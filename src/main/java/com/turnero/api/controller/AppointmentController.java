package com.turnero.api.controller;

import com.turnero.api.dto.AppointmentCancelRequestDto;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.dto.AppointmentUpdateRequestDto;
import com.turnero.api.dto.AppointmentReadDto;
import com.turnero.api.dto.AppointmentPageResponseDto;
import com.turnero.api.service.AppointmentReadService;
import org.springframework.util.MultiValueMap;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import com.turnero.api.model.Appointment;
import com.turnero.api.openapi.*;
import com.turnero.api.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Appointments",
        description = "Endpoints para gestionar turnos"
)
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentReadService reads;

    public AppointmentController(AppointmentService appointmentService, AppointmentReadService reads) {
        this.reads = reads;
        this.appointmentService = appointmentService;
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
            description = "Colección {data,page}. date YYYY-MM-DD o from/to ISO con offset, excluyentes; hoy del negocio por defecto. Rango [from,to), máximo 31 días calendario. Filtros status repetido/CSV, staff_member_id, customer_id, service_offering_id y source. Página desde 0, size 50 (1–100), sort starts_at,asc o starts_at,desc; desempate id ascendente. Validación 400. Referencias históricas limitadas al negocio; horarios locales serializados con offset IANA. Escrituras mantienen su DTO."
    )
    @ApiFindAllResponses
    @Parameters({
        @Parameter(name = "date", description = "Día del negocio; excluyente con from/to", schema = @Schema(type = "string", format = "date")),
        @Parameter(name = "from", description = "Inicio incluido ISO 8601 con offset; requiere to", schema = @Schema(type = "string", format = "date-time")),
        @Parameter(name = "to", description = "Fin exclusivo ISO 8601 con offset; máximo 31 días calendario", schema = @Schema(type = "string", format = "date-time")),
        @Parameter(name = "status", description = "Repetible o CSV: PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW. Todos por defecto", schema = @Schema(type = "string")),
        @Parameter(name = "staff_member_id", schema = @Schema(type = "integer", minimum = "1")),
        @Parameter(name = "customer_id", schema = @Schema(type = "integer", minimum = "1")),
        @Parameter(name = "service_offering_id", schema = @Schema(type = "integer", minimum = "1")),
        @Parameter(name = "source", schema = @Schema(type = "string", allowableValues = {"ADMIN", "PUBLIC_BOOKING"})),
        @Parameter(name = "page", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
        @Parameter(name = "size", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "50")),
        @Parameter(name = "sort", schema = @Schema(type = "string", allowableValues = {"starts_at,asc", "starts_at,desc"}, defaultValue = "starts_at,asc"))
    })
    @GetMapping
    public ResponseEntity<AppointmentPageResponseDto> findAllAppointment(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        return ResponseEntity.ok(reads.list(params));
    }

    @Operation(
            summary = "Obtener turno por ID",
            description = "Devuelve AppointmentReadDto, igual a cada elemento de GET colección. Referencias limitadas al negocio autenticado, 404 para inexistentes o ajenos; ID inválido 400. No modifica contratos de escritura."
    )
    @ApiFindByIdResponses
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentReadDto> findAppointment(@PathVariable Long id) {
        return ResponseEntity.ok(reads.find(id));
    }

    @Operation(
            summary = "Actualizar turno",
            description = "Actualiza parcialmente los datos permitidos de un turno específico"
    )
    @ApiUpdateResponses
    @PatchMapping("/{id}")
    public ResponseEntity<AppointmentResponseDto> updateAppointment(@PathVariable Long id, @Valid @RequestBody AppointmentUpdateRequestDto request) {
        AppointmentResponseDto response =
                appointmentService.updateAppointment(id, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<AppointmentResponseDto> confirmAppointment(@PathVariable Long id) {
        AppointmentResponseDto response = appointmentService.confirmAppointment(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponseDto> cancelAppointment(@PathVariable Long id, @RequestBody AppointmentCancelRequestDto request) {
        AppointmentResponseDto response = appointmentService.cancelAppointment(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponseDto> completeAppointment(@PathVariable Long id) {
        AppointmentResponseDto response = appointmentService.completeAppointment(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<AppointmentResponseDto> markAppointmentAsNoShow(@PathVariable Long id) {
        AppointmentResponseDto response = appointmentService.markAppointmentAsNoShow(id);
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

package com.turnero.api.controller;

import com.turnero.api.dto.AvailabilitySlotResponseDto;
import com.turnero.api.openapi.ApiFindAllResponses;
import com.turnero.api.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Tag(
        name = "Availability",
        description = "Endpoints para consultar disponibilidad de turnos"
)
@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @Operation(
            summary = "Consultar slots disponibles",
            description = "Obtiene los horarios disponibles para un profesional y un servicio"
    )
    @ApiFindAllResponses
    @GetMapping("/slots")
    public ResponseEntity<List<AvailabilitySlotResponseDto>> getAvailableSlots(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(name = "service_offering_id") Long serviceOfferingId,
            @RequestParam(name = "staff_member_id") Long staffMemberId,
            @RequestParam(name = "exclude_appointment_id", required = false) Long excludeAppointmentId
    ) {
        validateDateParameters(date, from, to);

        var resolvedFrom = date != null ? date : from;
        var resolvedTo = date != null ? date : to;

        var availableSlots = availabilityService.getAvailableSlots(
                resolvedFrom,
                resolvedTo,
                serviceOfferingId,
                staffMemberId,
                excludeAppointmentId
        );

        return ResponseEntity.ok(availableSlots);
    }

    private void validateDateParameters(
            LocalDate date,
            LocalDate from,
            LocalDate to
    ) {
        var hasDate = date != null;
        var hasFrom = from != null;
        var hasTo = to != null;

        if (hasDate && (hasFrom || hasTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "date cannot be combined with from or to"
            );
        }

        if (!hasDate && (!hasFrom || !hasTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide date or both from and to"
            );
        }
    }
}

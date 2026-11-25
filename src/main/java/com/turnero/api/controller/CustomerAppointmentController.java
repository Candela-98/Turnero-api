package com.turnero.api.controller;

import com.turnero.api.auth.CustomerSessionCookie;
import com.turnero.api.dto.CustomerAppointmentPageResponseDto;
import com.turnero.api.dto.CustomerAppointmentRequestDto;
import com.turnero.api.dto.PublicAppointmentResponseDto;
import com.turnero.api.service.CustomerAppointmentBookingService;
import jakarta.validation.Valid;
import com.turnero.api.service.CustomerAppointmentReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/businesses/{slug}/appointments")
@RequiredArgsConstructor
public class CustomerAppointmentController {
    private final CustomerAppointmentReadService appointments;
    private final CustomerSessionCookie sessionCookie;
    private final CustomerAppointmentBookingService booking;

    @Operation(summary = "Crear una reserva con la ficha de la sesión cliente",
            description = "No acepta ficha ni titular en el cuerpo. Reutiliza las reglas de reserva pública. "
                    + "201 creado; 400 cuerpo inválido; 401 sesión inválida; 403 negocio o ficha bloqueados; "
                    + "404 recurso inexistente; 409 horario no disponible. Horario local del negocio.")
    @PostMapping
    public ResponseEntity<PublicAppointmentResponseDto> create(@PathVariable String slug,
            @Valid @RequestBody CustomerAppointmentRequestDto body, HttpServletRequest request) {
        return ResponseEntity.status(201).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(booking.createCustomerAppointment(slug, sessionCookie.requiredToken(request), body));
    }

    @Operation(summary = "Consultar turnos propios del negocio",
            description = "Titularidad explícita por identidad y negocio. Colección {data,page}, página desde 0, "
                    + "size 50 (1–100), sort starts_at,asc o starts_at,desc (default desc), desempate id ascendente. "
                    + "Incluye todas las fechas y estados propios. Horarios con offset de la zona del negocio. "
                    + "401 para sesión inválida, 403 para otro negocio/inactivo, 404 para slug inexistente y 400 para parámetros inválidos.")
    @Parameters({
            @Parameter(name = "page", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
            @Parameter(name = "size", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "50")),
            @Parameter(name = "sort", schema = @Schema(type = "string",
                    allowableValues = {"starts_at,asc", "starts_at,desc"}, defaultValue = "starts_at,desc"))
    })
    @GetMapping
    public ResponseEntity<CustomerAppointmentPageResponseDto> list(@PathVariable String slug,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params, HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(appointments.list(slug, sessionCookie.requiredToken(request), params));
    }
}

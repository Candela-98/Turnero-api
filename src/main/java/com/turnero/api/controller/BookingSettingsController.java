package com.turnero.api.controller;

import com.turnero.api.dto.BookingSettingsResponseDto;
import com.turnero.api.dto.BookingSettingsUpdateRequestDto;
import com.turnero.api.mapper.BookingSettingsMapper;
import com.turnero.api.service.BookingSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking-settings")
@RequiredArgsConstructor
@Tag(name = "Booking Settings", description = "Endpoints para configurar reglas de reserva del negocio actual")
public class BookingSettingsController {
    private final BookingSettingsService bookingSettingsService;
    private final BookingSettingsMapper bookingSettingsMapper;

    @GetMapping
    @Operation(summary = "Obtener reglas de reserva")
    public ResponseEntity<BookingSettingsResponseDto> getBookingSettings() {
        return ResponseEntity.ok(bookingSettingsMapper.toResponseDto(
                bookingSettingsService.getCurrentBookingSettings()
        ));
    }

    @PatchMapping
    @Operation(summary = "Actualizar reglas de reserva")
    public ResponseEntity<BookingSettingsResponseDto> updateBookingSettings(
            @Valid @RequestBody BookingSettingsUpdateRequestDto request
    ) {
        return ResponseEntity.ok(bookingSettingsMapper.toResponseDto(
                bookingSettingsService.updateCurrentBookingSettings(request)
        ));
    }
}

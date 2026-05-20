package com.turnero.api.dto;

import com.turnero.api.model.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentResponseDto {

    @Schema(description = "Appointment ID", example = "1")
    private Long id;

    @Schema(description = "ID of the customer requesting the appointment", example = "1")
    private Long customerId;

    @Schema(description = "ID of the service for the appointment", example = "1")
    private Long serviceId;

    @Schema(description = "ID of the staff member assigned to the appointment", example = "1")
    private Long staffMemberId;

    @Schema(description = "Appointment date and time", example = "2024-12-31T14:30:00")
    private LocalDateTime dateTime;

    @Schema(description = "Appointment duration in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Appointment status", example = "PENDING")
    private AppointmentStatus status;

    @Schema(description = "Additional notes for the appointment", example = "The customer prefers an afternoon schedule\")")
    private String notes;

    @Schema(description = "Appointment creation date and time", example = "2024-12-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Date and time of the last appointment update", example = "2024-12-15T12:00:00")
    private LocalDateTime updatedAt;
}

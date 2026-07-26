package com.turnero.api.dto;

import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AppointmentSource;
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

    @Schema(description = "ID of the service offering for the appointment", example = "1")
    private Long serviceOfferingId;

    @Schema(description = "ID of the staff member assigned to the appointment", example = "1")
    private Long staffMemberId;

    @Schema(description = "Appointment date and time", example = "2024-12-31T14:30:00")
    private LocalDateTime startsAt;

    @Schema(description = "Appointment end date and time", example = "2024-12-31T15:00:00")
    private LocalDateTime endsAt;

    @Schema(description = "Appointment duration in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Appointment status", example = "PENDING")
    private AppointmentStatus status;

    @Schema(description = "Appointment source", example = "PUBLIC_BOOKING")
    private AppointmentSource source;

    @Schema(description = "Appointment price in cents", example = "1500000")
    private int priceCents;

    @Schema(description = "Customer notes for the appointment", example = "The customer prefers an afternoon schedule")
    private String customerNotes;

    @Schema(description = "Internal notes for the appointment", example = "VIP customer")
    private String internalNotes;

    @Schema(description = "Reason provided when the appointment was cancelled", example = "Customer requested cancellation")
    private String cancellationReason;

    @Schema(description = "Appointment creation date and time", example = "2024-12-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Date and time of the last appointment update", example = "2024-12-15T12:00:00")
    private LocalDateTime updatedAt;
}

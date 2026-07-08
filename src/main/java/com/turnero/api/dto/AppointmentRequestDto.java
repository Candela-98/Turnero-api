package com.turnero.api.dto;

import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AppointmentSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class AppointmentRequestDto {

    @Schema(description = "ID of the customer requesting the appointment", example = "1")
    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @Schema(description = "Customer name when the customer is not registered", example = "Juan Pérez")
    private String customerName;

    @Schema(description = "Customer email when the customer is not registered", example = "juan@email.com")
    @Email(message = "Customer email must be valid")
    private String customerEmail;

    @Schema(description = "ID of the service offering for the appointment", example = "1")
    @NotNull(message = "Service offering ID is required")
    private Long serviceOfferingId;

    @Schema(description = "ID of the staff member assigned to the appointment", example = "1")
    @NotNull(message = "Staff member ID is required")
    private Long staffMemberId;

    @Schema(description = "Appointment date and time", example = "2024-12-31T14:30:00")
    @NotNull(message = "Appointment date and time is required")
    @Future(message = "The date and time must be in the future")
    private LocalDateTime startsAt;

    @Schema(description = "Appointment end date and time", example = "2024-12-31T15:00:00")
    private LocalDateTime endsAt;

    @Schema(description = "Appointment duration in minutes", example = "30")
    @Min(value = 1, message = "Minimum duration is 1 minute")
    private int durationMinutes;

    @Schema(description = "Appointment status", example = "PENDING")
    @NotNull(message = "Appointment status is required")
    private AppointmentStatus status;

    @Schema(description = "Appointment source", example = "PUBLIC_BOOKING")
    private AppointmentSource source;

    @Schema(description = "Appointment price in cents", example = "1500000")
    private int priceCents;

    @Schema(description = "Customer notes for the appointment", example = "The customer prefers an afternoon schedule")
    private String customerNotes;

    @Schema(description = "Internal notes for the appointment", example = "VIP customer")
    private String internalNotes;

}

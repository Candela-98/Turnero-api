package com.turnero.api.dto;

import com.turnero.api.model.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "ID of the service for the appointment", example = "1")
    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @Schema(description = "ID of the staff member assigned to the appointment", example = "1")
    @NotNull(message = "Staff member ID is required")
    private Long staffMemberId;

    @Schema(description = "Appointment date and time", example = "2024-12-31T14:30:00")
    @NotNull(message = "Appointment date and time is required")
    @Future(message = "The date and time must be in the future")
    private LocalDateTime dateTime;

    @Schema(description = "Appointment duration in minutes", example = "30")
    @Min(value = 1, message = "Minimum duration is 1 minute")
    private int durationMinutes;

    @Schema(description = "Appointment status", example = "PENDING")
    @NotNull(message = "Appointment status is required")
    private AppointmentStatus status;

    @Schema(description = "Additional notes for the appointment", example = "The customer prefers an afternoon schedule")
    private String notes;


}

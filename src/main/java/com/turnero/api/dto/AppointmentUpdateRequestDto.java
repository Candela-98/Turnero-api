package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentUpdateRequestDto {

    @Schema(description = "ID of the customer requesting the appointment", example = "1")
    private Long customerId;

    @Schema(description = "ID of the service offering for the appointment", example = "1")
    private Long serviceOfferingId;

    @Schema(description = "ID of the staff member assigned to the appointment", example = "1")
    private Long staffMemberId;

    @Schema(description = "Appointment date and time", example = "2026-12-31T14:30:00")
    @Future(message = "The date and time must be in the future")
    @JsonProperty("starts_at")
    private LocalDateTime startsAt;

    @Schema(description = "Customer notes for the appointment", example = "The customer prefers an afternoon schedule")
    private String customerNotes;

    @Schema(description = "Internal notes for the appointment", example = "VIP customer")
    private String internalNotes;
}

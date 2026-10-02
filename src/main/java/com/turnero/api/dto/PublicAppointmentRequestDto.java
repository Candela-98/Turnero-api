package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicAppointmentRequestDto {

    @NotNull(message = "Service offering ID is required")
    private Long serviceOfferingId;

    @NotBlank(message = "Staff member ID is required")
    private String staffMemberId;

    @NotNull(message = "Appointment date and time is required")
    @Future(message = "The date and time must be in the future")
    private LocalDateTime startsAt;

    @Valid
    @NotNull(message = "Customer is required")
    private PublicAppointmentCustomerRequestDto customer;

    private String customerNotes;
}

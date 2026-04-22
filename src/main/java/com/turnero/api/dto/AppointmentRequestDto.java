package com.turnero.api.dto;

import com.turnero.api.model.AppointmentStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class AppointmentRequestDto {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotNull(message = "Staff member ID is required")
    private Long staffMemberId;

    @NotNull(message = "Appointment date and time is required")
    @Future(message = "The date and time must be in the future")
    private LocalDateTime dateTime;

    @Min(value = 1, message = "Minimum duration is 1 minute")
    private int durationMinutes;

    @NotNull(message = "Appointment status is required")
    private AppointmentStatus status;

    private String notes;


}

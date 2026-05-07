package com.turnero.api.dto;

import com.turnero.api.model.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentResponseDto {
    private Long id;
    private Long customerId;
    private Long serviceId;
    private Long staffMemberId;
    private LocalDateTime dateTime;
    private int durationMinutes;
    private AppointmentStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

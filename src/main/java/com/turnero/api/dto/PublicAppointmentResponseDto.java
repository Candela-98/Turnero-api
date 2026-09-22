package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicAppointmentResponseDto {

    private Long appointmentId;

    private Long serviceOfferingId;

    private Long staffMemberId;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private int durationMinutes;

    private int priceCents;

    private AppointmentStatus status;

    private String cancelToken;
}

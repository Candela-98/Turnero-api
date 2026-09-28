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
public class PublicCancellationAppointmentDto {

    private String serviceName;
    private String staffMemberName;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private AppointmentStatus status;
}

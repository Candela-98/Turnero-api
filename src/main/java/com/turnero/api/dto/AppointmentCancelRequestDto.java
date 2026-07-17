package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppointmentCancelRequestDto {

    private String cancellationReason;
}

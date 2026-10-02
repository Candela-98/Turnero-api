package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicCancellationResponseDto {

    private PublicCancellationBusinessDto business;
    private PublicCancellationAppointmentDto appointment;

    @JsonProperty("can_cancel")
    private boolean isCanCancel;
}

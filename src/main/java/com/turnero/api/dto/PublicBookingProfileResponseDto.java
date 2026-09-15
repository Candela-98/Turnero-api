package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicBookingProfileResponseDto {

    @Schema(description = "Business information")
    private PublicBusinessResponseDto business;

    @Schema(description = "Booking settings for the business")
    private PublicBookingSettingsResponseDto bookingSettings;

}

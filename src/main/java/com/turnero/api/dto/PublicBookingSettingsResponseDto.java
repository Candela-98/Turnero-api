package com.turnero.api.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicBookingSettingsResponseDto {

    @Schema(description = "Indicates whether the business accepts public reservations.", example = "true")
    private boolean publicBookingEnabled;

    @Schema(description = "Indicates how many days in advance you can make a reservation.", example = "7")
    private int bookingWindowDays;

    @Schema(description = "Indicates the minimum number of hours' notice required for a reservation.", example = "3")
    private int minNoticeHours;

    @Schema(description = "Range used to generate possible schedules", example = "30")
    private int slotIntervalMinutes;

    @Schema(description = "Indicates whether reservations require manual confirmation", example = "TRUE")
    private boolean manualConfirmationEnabled;
}

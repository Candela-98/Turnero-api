package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BookingSettingsUpdateRequestDto {
    @Schema(description = "Enable or disable public booking", example = "true")
    private Boolean publicBookingEnabled;

    @Schema(description = "Unsupported in MVP; sending true is rejected", example = "false")
    @AssertFalse(message = "Customer login is not supported in the MVP")
    private Boolean requiresCustomerLogin;

    @Schema(description = "Maximum booking window in days", example = "14")
    @Min(value = 1, message = "Booking window must be at least 1 day")
    private Integer bookingWindowDays;

    @Schema(description = "Minimum booking notice in hours", example = "3")
    @Min(value = 0, message = "Minimum notice must be greater than or equal to 0")
    private Integer minNoticeHours;

    @Schema(description = "Minimum cancellation notice in hours", example = "3")
    @Min(value = 0, message = "Cancellation notice must be greater than or equal to 0")
    private Integer cancellationNoticeHours;

    @Schema(description = "Slot interval in minutes; allowed values are 15, 30, 45 or 60", example = "30")
    private Integer slotIntervalMinutes;

    @Schema(description = "Whether bookings require manual confirmation", example = "true")
    private Boolean manualConfirmationEnabled;
}

package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BookingSettingsResponseDto {
    @Schema(description = "Whether public booking is enabled", example = "true")
    private boolean publicBookingEnabled;
    @Schema(description = "Whether customer login is required; always false in MVP", example = "false")
    private boolean requiresCustomerLogin;
    @Schema(description = "Maximum number of days in advance that a customer can book", example = "7")
    private int bookingWindowDays;
    @Schema(description = "Minimum notice required before a booking", example = "3")
    private int minNoticeHours;
    @Schema(description = "Minimum notice required to cancel a booking", example = "3")
    private int cancellationNoticeHours;
    @Schema(description = "Interval between appointment slots in minutes", example = "30")
    private int slotIntervalMinutes;
    @Schema(description = "Whether bookings require manual confirmation", example = "true")
    private boolean manualConfirmationEnabled;
    @Schema(description = "Whether WhatsApp reminders are enabled; always false in MVP", example = "false")
    private boolean whatsappRemindersEnabled;
    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}

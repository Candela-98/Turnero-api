package com.turnero.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AvailabilitySlotResponseDto {
    @Schema(description = "Start date and time of the available slot", example = "2026-08-01T10:00:00")
    private LocalDateTime startsAt;

    @Schema(description = "End date and time of the available slot", example = "2026-08-01T11:00:00")
    private LocalDateTime endsAt;

    @Schema(description = "Indicates whether the slot is available", example = "true")
    private boolean available;

}

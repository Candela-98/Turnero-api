package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.DayOfWeek;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class StaffWorkingHoursResponseDto {
    @NotNull(message = "Day of week is required")
    @Schema(description = "Day of the week", example = "MONDAY")
    @JsonProperty("day_of_week")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Start time", example = "09:00")
    @JsonProperty("starts_at")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startsAt;

    @Schema(description = "End time", example = "18:00")
    @JsonProperty("ends_at")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endsAt;

    @Schema(description = "Whether the staff member is available that day", example = "true")
    @JsonProperty("is_available")
    private boolean isAvailable;

    @JsonIgnore
    public boolean isAvailable() {
        return isAvailable;
    }

    @JsonProperty("is_available")
    public boolean getIsAvailable() {
        return isAvailable;
    }
}

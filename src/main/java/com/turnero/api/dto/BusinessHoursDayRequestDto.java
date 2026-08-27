package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.DayOfWeek;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class BusinessHoursDayRequestDto {
    @NotNull(message = "Day of week is required")
    @JsonProperty("day_of_week")
    @Schema(example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @JsonProperty("opens_at")
    @JsonFormat(pattern = "HH:mm")
    @Schema(example = "09:00")
    private LocalTime opensAt;

    @JsonProperty("closes_at")
    @JsonFormat(pattern = "HH:mm")
    @Schema(example = "20:00")
    private LocalTime closesAt;

    @NotNull(message = "Closed status is required")
    @JsonProperty("is_closed")
    @Schema(example = "false")
    private Boolean isClosed;
}

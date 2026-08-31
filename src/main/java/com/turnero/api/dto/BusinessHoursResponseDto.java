package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.DayOfWeek;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class BusinessHoursResponseDto {
    private Long id;

    @JsonProperty("day_of_week")
    private DayOfWeek dayOfWeek;

    @JsonProperty("opens_at")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime opensAt;

    @JsonProperty("closes_at")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime closesAt;

    private boolean isClosed;

    @JsonIgnore
    public boolean isClosed() {
        return isClosed;
    }

    @JsonProperty("is_closed")
    @Schema(example = "false")
    public boolean getIsClosed() {
        return isClosed;
    }
}

package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BusinessHoursReplaceRequestDto {
    @NotNull(message = "Hours are required")
    @Size(min = 7, max = 7, message = "Business hours must contain exactly 7 days")
    @Valid
    @JsonProperty("hours")
    private List<BusinessHoursDayRequestDto> hours;
}

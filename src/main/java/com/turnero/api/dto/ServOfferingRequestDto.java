package com.turnero.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ServOfferingRequestDto {

    private Long Id;

    @NotNull(message = "The service offering duration is required")
    @Min(value = 1, message = "The duration minimum is 1 minute")
    private int durationMinutes;

    @NotNull(message = "The service offering name is required")
    private String name;

    @NotNull(message = "The service offering price is required")
    @Positive(message = "The price must be greater than 0")
    private double price;

}

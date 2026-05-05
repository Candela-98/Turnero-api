package com.turnero.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ServOfferingRequestDto {

    @Min(value = 1, message = "The duration minimum is 1 minute")
    private int durationMinutes;

    @NotBlank(message = "The service offering name is required")
    @Size(max = 100, message = "The service offering name must have at most 100 characters")
    private String name;

    @Positive(message = "The price must be greater than 0")
    private double price;

}

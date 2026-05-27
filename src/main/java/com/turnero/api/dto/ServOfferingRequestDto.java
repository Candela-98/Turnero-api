package com.turnero.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ServOfferingRequestDto {

    @Schema(description = "Service duration in minutes", example = "30")
    @Min(value = 1, message = "The duration minimum is 1 minute")
    private int durationMinutes;

    @Schema(description = "The service offering name", example = "Haircut")
    @NotBlank(message = "The service offering name is required")
    @Size(max = 100, message = "The service offering name must have at most 100 characters")
    private String name;

    @Schema(description = "Price of the service offering", example = "15000.00")
    @Positive(message = "The price must be greater than 0")
    private double price;

}

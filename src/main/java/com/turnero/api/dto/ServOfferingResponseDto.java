package com.turnero.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServOfferingResponseDto {

    @Schema(description = "Service offering ID", example = "1")
    private Long id;

    @Schema(description = "Name of the service offering", example = "Haircut")
    private String name;

    @Schema(description = "Service duration in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Price of the service offering", example = "15000.00")
    private double price;
}

package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ServOfferingRequestDto {

    @Schema(description = "Service duration in minutes", example = "30")
    @Min(value = 1, message = "The duration minimum is 1 minute")
    @JsonProperty("duration_minutes")
    private int durationMinutes;

    @Schema(description = "The service offering name", example = "Haircut")
    @NotBlank(message = "The service offering name is required")
    @Size(max = 100, message = "The service offering name must have at most 100 characters")
    private String name;

    @Schema(description = "The service offering category", example = "Hair")
    @Size(max = 100, message = "The service offering category must have at most 100 characters")
    private String category;

    @Schema(description = "Price of the service offering in cents", example = "1500000")
    @PositiveOrZero(message = "The price must be greater than or equal to 0")
    @JsonProperty("price_cents")
    private int priceCents;

    @Schema(description = "The service offering status", example = "ACTIVE")
    private ServiceOfferingStatus status;

}

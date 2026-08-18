package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServOfferingUpdateRequestDto {

    @Schema(description = "The service offering name", example = "Haircut and beard")
    @Size(min = 1, max = 100, message = "The service offering name must have between 1 and 100 characters")
    private String name;

    @Schema(description = "The service offering category", example = "Hair")
    @Size(max = 100, message = "The service offering category must have at most 100 characters")
    private String category;

    @Schema(description = "Service duration in minutes", example = "45")
    @Min(value = 1, message = "The duration minimum is 1 minute")
    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @Schema(description = "Price of the service offering in cents", example = "12000")
    @PositiveOrZero(message = "The price must be greater than or equal to 0")
    @JsonProperty("price_cents")
    private Integer priceCents;

    @Schema(description = "The service offering status", example = "ACTIVE")
    private ServiceOfferingStatus status;
}

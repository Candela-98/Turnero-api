package com.turnero.api.dto;

import com.turnero.api.model.enums.ServiceOfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ServOfferingRequestDto {

    @Schema(description = "Business ID", example = "1")
    private Long businessId;

    @Schema(description = "Service duration in minutes", example = "30")
    @Min(value = 1, message = "The duration minimum is 1 minute")
    private int durationMinutes;

    @Schema(description = "The service offering name", example = "Haircut")
    @NotBlank(message = "The service offering name is required")
    @Size(max = 100, message = "The service offering name must have at most 100 characters")
    private String name;

    @Schema(description = "The service offering category", example = "Hair")
    @Size(max = 100, message = "The service offering category must have at most 100 characters")
    private String category;

    @Schema(description = "Price of the service offering in cents", example = "1500000")
    @Positive(message = "The price must be greater than 0")
    private int priceCents;

    @Schema(description = "The service offering status", example = "ACTIVE")
    private ServiceOfferingStatus status;

}

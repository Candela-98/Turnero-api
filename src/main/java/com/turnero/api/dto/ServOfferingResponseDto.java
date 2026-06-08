package com.turnero.api.dto;

import com.turnero.api.model.enums.ServiceOfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServOfferingResponseDto {

    @Schema(description = "Service offering ID", example = "1")
    private Long id;

    @Schema(description = "Business ID", example = "1")
    private Long businessId;

    @Schema(description = "Name of the service offering", example = "Haircut")
    private String name;

    @Schema(description = "Category of the service offering", example = "Hair")
    private String category;

    @Schema(description = "Service duration in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Price of the service offering in cents", example = "1500000")
    private int priceCents;

    @Schema(description = "The service offering status", example = "ACTIVE")
    private ServiceOfferingStatus status;
}

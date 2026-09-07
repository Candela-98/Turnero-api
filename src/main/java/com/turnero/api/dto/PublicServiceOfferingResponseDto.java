package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicServiceOfferingResponseDto {

    @Schema(description = "Unique identifier of the service offering", example = "1")
    private Long id;

    @Schema(description = "Name of the service offering", example = "Haircut")
    private String name;

    @Schema(description = "Category of the service offering", example = "Hair Services")
    private String category;

    @Schema(description = "Duration of the service offering in minutes", example = "30")
    private Integer durationMinutes;

    @Schema(description = "Price of the service offering in cents", example = "25000")
    private Integer priceCents;

    private List<PublicStaffMemberResponseDto> staffMembers;
}

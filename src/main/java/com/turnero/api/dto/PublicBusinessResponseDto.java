package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicBusinessResponseDto {

    @Schema(description = "Business Name", example = "Barber Studio")
    private String name;

    @Schema(description = "Public identifier of the business", example = "barber-studio")
    private String slug;

    @Schema(description = "The industry to which the business belongs", example = "Barber")
    private String industry;

    @Schema(description = "Time zone in which the business operates", example = "America/Argentina/Buenos_Aires")
    private String timezone;
}

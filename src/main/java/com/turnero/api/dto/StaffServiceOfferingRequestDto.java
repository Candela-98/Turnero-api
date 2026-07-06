package com.turnero.api.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StaffServiceOfferingRequestDto {
    @Schema(description = "List of service offering IDs assigned to the staff member", example = "[1, 2, 3]")
    @NotNull(message = "The service offering IDs list is required")
    private List<Long> serviceOfferingIds;
}

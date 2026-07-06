package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.StaffMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StaffMemberResponseDto {

    @Schema(description = "Staff member ID", example = "1")
    private Long id;

    @Schema(description = "Business ID", example = "1")
    private Long businessId;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "The staff member's name", example = "Maria Oliveira")
    private String name;

    @Schema(description = "The staff member's role label", example = "Senior barber")
    private String roleLabel;

    @Schema(description = "The staff member's specialty", example = "Hairdresser")
    private String specialty;

    @Schema(description = "The staff member's avatar URL", example = "https://example.com/avatar.png")
    private String avatarUrl;

    @Schema(description = "The staff member's status", example = "ACTIVE")
    private StaffMemberStatus status;
}

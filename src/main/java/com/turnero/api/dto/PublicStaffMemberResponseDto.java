package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicStaffMemberResponseDto {

    @Schema(description = "Unique identifier of the staff member", example = "1")
    private Long id;

    @Schema(description = "Name of the staff member", example = "John Doe")
    private String name;

    @Schema(description = "Role of the staff member", example = "Barber")
    private String roleLabel;

    @Schema(description = "Specialty of the staff member", example = "Haircut, Beard Trim")
    private String specialty;

    @Schema(description = "Avatar URL of the staff member", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
}

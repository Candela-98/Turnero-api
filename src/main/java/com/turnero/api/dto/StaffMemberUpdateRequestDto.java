package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.StaffMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StaffMemberUpdateRequestDto {

    @Schema(description = "The staff member's name", example = "Maria Oliveira")
    @Size(min = 1, max = 100, message = "The staffmember's name must have between 1 and 100 characters.")
    private String name;

    @Schema(description = "The staff member's role label", example = "Senior barber")
    @Size(max = 100, message = "The staffmember's role label must have at most 100 characters.")
    @JsonProperty("role_label")
    private String roleLabel;

    @Schema(description = "The staff member's specialty", example = "Hairdresser")
    @Size(min = 1, max = 150, message = "The staffmember's specialty must have between 1 and 150 characters.")
    private String specialty;

    @Schema(description = "The staff member's avatar URL", example = "https://example.com/avatar.png")
    @Size(max = 255, message = "The staffmember's avatar URL must have at most 255 characters.")
    @JsonProperty("avatar_url")
    private String avatarUrl;

    @Schema(description = "The staff member's status", example = "ACTIVE")
    private StaffMemberStatus status;
}

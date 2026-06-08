package com.turnero.api.dto;

import com.turnero.api.model.enums.StaffMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class StaffMemberRequestDto {

    @Schema(description = "Business ID", example = "1")
    private Long businessId;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "The staffmember's name", example = "Maria Oliveira")
    @NotBlank(message = "The name of the staffmember is mandatory.")
    @Size(max = 100, message = "The staffmember's name must have at most 100 characters.")
    private String name;

    @Schema(description = "The staffmember's role label", example = "Senior barber")
    @Size(max = 100, message = "The staffmember's role label must have at most 100 characters.")
    private String roleLabel;

    @Schema(description = "The staffmember's specialty", example = "Hairdresser")
    @NotBlank(message = "The staffmember's specialty is mandatory.")
    @Size(max = 150, message = "The staffmember's specialty must have at most 150 characters.")
    private String specialty;

    @Schema(description = "The staffmember's avatar URL", example = "https://example.com/avatar.png")
    @Size(max = 255, message = "The staffmember's avatar URL must have at most 255 characters.")
    private String avatarUrl;

    @Schema(description = "The staffmember's status", example = "ACTIVE")
    private StaffMemberStatus status;

}

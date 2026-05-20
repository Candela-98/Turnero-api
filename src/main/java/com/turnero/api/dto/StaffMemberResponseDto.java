package com.turnero.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffMemberResponseDto {

    @Schema(description = "Staff member ID", example = "1")
    private Long id;

    @Schema(description = "The staff member's name", example = "Maria Oliveira")
    private String name;

    @Schema(description = "The staff member's specialty", example = "Hairdresser")
    private String specialty;

    @Schema(description = "The staff member's license", example = "123456789")
    private String license;
}

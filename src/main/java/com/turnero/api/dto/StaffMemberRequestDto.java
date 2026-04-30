package com.turnero.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class StaffMemberRequestDto {

    @NotBlank(message = "The name of the staffmember is mandatory.")
    @Size(max = 100, message = "The staffmember's name must have at most 100 characters.")
    private String nameStaffMember;

    @NotBlank(message = "The staffmember's specialty is mandatory.")
    @Size(max = 150, message = "The staffmember's specialty must have at most 150 characters.")
    private String specialty;

    @NotBlank(message = "The staffmember's license is mandatory.")
    @Size(max = 30, message = "The staffmember's license must have at most 30 characters.")
    private String license;

}

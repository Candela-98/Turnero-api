package com.turnero.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class StaffMemberRequestDto {

    private Long staffMemberId;

    @NotNull(message = "The name of the staffmember is mandatory.")
    private String nameStaffMember;

    @NotNull(message = "The staffmember's specialty is mandatory.")
    private String specialty;

    @NotNull(message = "The staffmember's license is mandatory.")
    private String license;


}

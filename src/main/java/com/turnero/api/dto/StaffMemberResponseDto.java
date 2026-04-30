package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffMemberResponseDto {

    private Long id;
    private String nameStaffMember;
    private String specialty;
    private String license;
}

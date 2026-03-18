package com.turnero.api.mapper;

import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.model.StaffMember;
import org.springframework.stereotype.Component;

@Component
public class StaffMemberMapper {

    public StaffMember toEntity(StaffMemberRequestDto dto){
        StaffMember staffMember = new StaffMember();
        staffMember.setId(dto.getStaffMemberId());
        staffMember.setName(dto.getNameStaffMember());
        staffMember.setSpecialty(dto.getSpecialty());
        staffMember.setLicense(dto.getLicense());

        return staffMember;
    }
}

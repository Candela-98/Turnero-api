package com.turnero.api.mapper;

import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.model.StaffMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StaffMemberMapper {

    @Mapping(source = "staffMemberId", target = "id")
    @Mapping(source = "nameStaffMember", target = "name")
    StaffMember toEntity(StaffMemberRequestDto dtoStaffMember);
}

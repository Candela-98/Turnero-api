package com.turnero.api.mapper;

import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.model.StaffMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StaffMemberMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "nameStaffMember", target = "name")
    @Mapping(source = "specialty", target = "specialty")
    @Mapping(source = "license", target = "license")
    StaffMember toEntity(StaffMemberRequestDto dtoStaffMember);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "nameStaffMember")
    StaffMemberResponseDto toResponseDto(StaffMember staffMember);

    List<StaffMemberResponseDto> toResponseDtoList(List<StaffMember> staffMembers);
}

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
    @Mapping(target = "businessId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StaffMember toEntity(StaffMemberRequestDto dtoStaffMember);

    StaffMemberResponseDto toResponseDto(StaffMember staffMember);

    List<StaffMemberResponseDto> toResponseDtoList(List<StaffMember> staffMembers);
}

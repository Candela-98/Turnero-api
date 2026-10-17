package com.turnero.api.service;

import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.dto.StaffWorkingHoursRequestDto;
import com.turnero.api.dto.StaffWorkingHoursResponseDto;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.enums.StaffMemberStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface StaffMemberService {

    StaffMember saveStaffMember(StaffMember staffMember);

    StaffMember findStaffMember(Long id);

    List<StaffMember> findAllStaffMember();

    Page<StaffMember> listStaffMembers(String q,
            StaffMemberStatus status, int page, int size,
            Sort ordering);

    StaffMember updateStaffMember(StaffMemberUpdateRequestDto staffMemberUpdateDto, Long id);

    List<StaffWorkingHoursResponseDto> getWorkingHours(Long staffMemberId);

    List<StaffWorkingHoursResponseDto> replaceWorkingHours(
            Long staffMemberId,
            List<StaffWorkingHoursRequestDto> workingHours
    );

    void deleteStaffMember(Long id);
}

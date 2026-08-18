package com.turnero.api.service;

import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.model.StaffMember;

import java.util.List;

public interface StaffMemberService {

    StaffMember saveStaffMember(StaffMember staffMember);

    StaffMember findStaffMember(Long id);

    List<StaffMember> findAllStaffMember();

    StaffMember updateStaffMember(StaffMemberUpdateRequestDto staffMemberUpdateDto, Long id);

    void deleteStaffMember(Long id);
}

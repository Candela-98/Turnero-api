package com.turnero.api.service;

import com.turnero.api.model.StaffMember;

import java.util.List;

public interface StaffMemberService {

    StaffMember saveStaffMember(StaffMember staffMember);

    StaffMember findStaffMember(Long id);

    List<StaffMember> findAllStaffMember();

    void updateStaffMember(StaffMember staffMember, Long id);

    void deleteStaffMember(Long id);
}

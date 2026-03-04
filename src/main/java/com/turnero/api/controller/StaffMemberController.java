package com.turnero.api.controller;

import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.StaffMember;
import com.turnero.api.service.StaffMemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staffmembers")
public class StaffMemberController {

    private final StaffMemberService staffMemberService;
    private final StaffMemberMapper staffMemberMapper;

    public StaffMemberController(StaffMemberService staffMemberService, StaffMemberMapper staffMemberMapper) {
        this.staffMemberService = staffMemberService;
        this.staffMemberMapper = staffMemberMapper;
    }

    @PostMapping
    public void saveStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.saveStaffMember(staff);
    }

    @GetMapping("/{id}")
    public StaffMember findStaffMember(@PathVariable Long id) {
        return staffMemberService.findStaffMember(id);
    }

    @PutMapping("/{id}")
    public void updateStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto, @PathVariable Long id) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.updateStaffMember(staff, id);
    }

    @GetMapping
    public List<StaffMember> findAllStaffMember() {
        return staffMemberService.findAllStaffMember();
    }

    @DeleteMapping("/{id}")
    public void deleteStaffMember(@PathVariable Long id) {
        staffMemberService.deleteStaffMember(id);
    }
}

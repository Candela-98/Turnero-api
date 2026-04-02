package com.turnero.api.controller;

import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.StaffMember;
import com.turnero.api.service.StaffMemberService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<StaffMember> saveStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.saveStaffMember(staff);
        return ResponseEntity.ok(staff);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffMember> findStaffMember(@PathVariable Long id) {
        var staff = staffMemberService.findStaffMember(id);
        return ResponseEntity.ok(staff);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffMember> updateStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto, @PathVariable Long id) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.updateStaffMember(staff, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<StaffMember>> findAllStaffMember() {
        var staffMembers = staffMemberService.findAllStaffMember();
        return ResponseEntity.ok(staffMembers);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<StaffMember> deleteStaffMember(@PathVariable Long id) {
        staffMemberService.deleteStaffMember(id);
        return ResponseEntity.noContent().build();
    }
}

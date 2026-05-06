package com.turnero.api.controller;

import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.StaffMember;
import com.turnero.api.service.StaffMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<StaffMemberResponseDto> saveStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.saveStaffMember(staff);
        var staffResponseDto = staffMemberMapper.toResponseDto(staff);

        return ResponseEntity.status(HttpStatus.CREATED).body(staffResponseDto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffMemberResponseDto> findStaffMember(@PathVariable Long id) {
        var staff = staffMemberService.findStaffMember(id);
            var staffResponseDto = staffMemberMapper.toResponseDto(staff);
        return ResponseEntity.ok(staffResponseDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffMember> updateStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto, @PathVariable Long id) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.updateStaffMember(staff, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<StaffMemberResponseDto>> findAllStaffMember() {
        var staffMembers = staffMemberService.findAllStaffMember();
        var staffMembersResponseDto = staffMemberMapper.toResponseDtoList(staffMembers);
        return ResponseEntity.ok(staffMembersResponseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<StaffMember> deleteStaffMember(@PathVariable Long id) {
        staffMemberService.deleteStaffMember(id);
        return ResponseEntity.noContent().build();
    }
}

package com.turnero.api.controller;

import com.turnero.api.dto.*;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.StaffMember;
import com.turnero.api.openapi.*;
import com.turnero.api.service.StaffMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Staff Members",
        description = "Endpoints para gestionar profesionales"
)
@RestController
@RequestMapping("/api/v1/staff-members")
public class StaffMemberController {

    private final StaffMemberService staffMemberService;
    private final StaffMemberMapper staffMemberMapper;

    public StaffMemberController(StaffMemberService staffMemberService, StaffMemberMapper staffMemberMapper) {
        this.staffMemberService = staffMemberService;
        this.staffMemberMapper = staffMemberMapper;
    }

    @Operation(
            summary = "Crear profesional",
            description = "Crea un nuevo profesional en el sistema"
    )
    @ApiCreateResponses
    @PostMapping
    public ResponseEntity<StaffMemberResponseDto> saveStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto) {
        var staff = staffMemberMapper.toEntity(staffDto);
        staffMemberService.saveStaffMember(staff);
        var staffResponseDto = staffMemberMapper.toResponseDto(staff);

        return ResponseEntity.status(HttpStatus.CREATED).body(staffResponseDto);
    }

    @Operation(
            summary = "Obtener profesional por ID",
            description = "Obtiene los detalles de un profesional específico utilizando su ID"
    )
    @ApiFindByIdResponses
    @GetMapping("/{id}")
    public ResponseEntity<StaffMemberResponseDto> findStaffMember(@PathVariable Long id) {
        var staff = staffMemberService.findStaffMember(id);
            var staffResponseDto = staffMemberMapper.toResponseDto(staff);
        return ResponseEntity.ok(staffResponseDto);
    }

    @GetMapping("/{id}/working-hours")
    public ResponseEntity<List<StaffWorkingHoursResponseDto>> getWorkingHours(@PathVariable Long id) {

        return ResponseEntity.ok(staffMemberService.getWorkingHours(id));
    }

    @Operation(
            summary = "Actualizar profesional",
            description = "Actualiza los detalles de un profesional existente utilizando su ID"
    )
    @ApiUpdateResponses
    @PatchMapping("/{id}")
    public ResponseEntity<StaffMemberResponseDto> updateStaffMember(@Valid @RequestBody StaffMemberUpdateRequestDto staffDto, @PathVariable Long id) {
        var updatedStaffMember = staffMemberService.updateStaffMember(staffDto, id);
        var staffResponseDto = staffMemberMapper.toResponseDto(updatedStaffMember);
        return ResponseEntity.ok(staffResponseDto);
    }

    @Operation(
            summary = "Reemplazar horarios semanales del profesional",
            description = "Reemplaza la semana completa de horarios de trabajo de un profesional"
    )
    @PutMapping("/{id}/working-hours")
    public ResponseEntity<List<StaffWorkingHoursResponseDto>> replaceWorkingHours(@PathVariable Long id,
            @Valid @RequestBody List<StaffWorkingHoursRequestDto> workingHours) {

        var updatedWorkingHours = staffMemberService.replaceWorkingHours(id, workingHours);

        return ResponseEntity.ok(updatedWorkingHours);
    }

    @Operation(
            summary = "Obtener todos los profesionales",
            description = "Obtiene una lista de todos los profesionales registrados en el sistema"
    )
    @ApiFindAllResponses
    @GetMapping
    public ResponseEntity<List<StaffMemberResponseDto>> findAllStaffMember() {
        var staffMembers = staffMemberService.findAllStaffMember();
        var staffMembersResponseDto = staffMemberMapper.toResponseDtoList(staffMembers);
        return ResponseEntity.ok(staffMembersResponseDto);
    }

    @Operation(
            summary = "Eliminar profesional",
            description = "Elimina un profesional específico utilizando su ID"
    )
    @ApiDeleteResponses
    @DeleteMapping("/{id}")
    public ResponseEntity<StaffMember> deleteStaffMember(@PathVariable Long id) {
        staffMemberService.deleteStaffMember(id);
        return ResponseEntity.noContent().build();
    }
}

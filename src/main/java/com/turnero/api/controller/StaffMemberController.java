package com.turnero.api.controller;

import com.turnero.api.dto.StaffMemberPageResponseDto;
import com.turnero.api.dto.StaffMemberLinkResponseDto;
import com.turnero.api.auth.AuthenticatedUserContext;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.dto.StaffWorkingHoursRequestDto;
import com.turnero.api.dto.StaffWorkingHoursResponseDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.openapi.*;
import com.turnero.api.service.StaffMemberService;
import com.turnero.api.service.StaffMemberSort;

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

    private final AuthenticatedUserContext authenticatedUserContext;
    private final StaffMemberService staffMemberService;
    private final StaffMemberMapper staffMemberMapper;

    public StaffMemberController(StaffMemberService staffMemberService, StaffMemberMapper staffMemberMapper, AuthenticatedUserContext authenticatedUserContext) {
        this.authenticatedUserContext = authenticatedUserContext;
        this.staffMemberService = staffMemberService;
        this.staffMemberMapper = staffMemberMapper;
    }

    @Operation(
            summary = "Crear profesional",
            description = "Crea un profesional activo y copia los horarios del negocio. Rol de hasta 80 caracteres. Si user_id ya está vinculado, devuelve 409 STAFF_USER_ALREADY_LINKED."
    )
    @ApiCreateResponses
    @ApiResponse(responseCode = "409", description = "STAFF_USER_ALREADY_LINKED: el usuario ya está vinculado a un profesional")
    @PostMapping
    public ResponseEntity<StaffMemberResponseDto> saveStaffMember(@Valid @RequestBody StaffMemberRequestDto staffDto) {
        var staff = staffMemberMapper.toEntity(staffDto);
        var saved = staffMemberService.saveStaffMember(staff);
        var staffResponseDto = staffMemberMapper.toResponseDto(saved);

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

    @Operation(summary = "Obtener mi profesional vinculado", description = "Consulta por usuario de sesión y negocio autenticado, independiente de filtros, estado y paginación. Devuelve staff_member null si no existe vinculación; incluye profesionales inactivos.")
    @GetMapping("/me")
    public ResponseEntity<StaffMemberLinkResponseDto> findCurrentStaffMember() {
        var member = staffMemberService.findStaffMemberForUser(authenticatedUserContext.getRequiredUser().getId());
        return ResponseEntity.ok(new StaffMemberLinkResponseDto(member.map(staffMemberMapper::toResponseDto).orElse(null)));
    }

    @Operation(summary = "Obtener semana laboral del profesional", description = "Array de siete días de lunes a domingo; los días faltantes se muestran no disponibles sin persistirlos. Horas HH:mm o null.")
    @ApiFindByIdResponses
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
            description = "Reemplaza la semana completa mediante un array de siete días únicos. Guarda de forma atómica y conserva IDs existentes. Días no disponibles normalizan horas a null. No modifica turnos existentes."
    )
    @PutMapping("/{id}/working-hours")
    public ResponseEntity<List<StaffWorkingHoursResponseDto>> replaceWorkingHours(@PathVariable Long id,
            @Valid @RequestBody List<StaffWorkingHoursRequestDto> workingHours) {

        var updatedWorkingHours = staffMemberService.replaceWorkingHours(id, workingHours);

        return ResponseEntity.ok(updatedWorkingHours);
    }

    @Operation(
            summary = "Obtener todos los profesionales",
            description = "Filtra por nombre, rol, especialidad y estado del negocio autenticado. Página desde 0, tamaño 1–100 (20 por defecto), q hasta 100 caracteres; orden name,asc o name,desc con id ascendente como desempate. Parámetros inválidos devuelven 400."
    )
    @ApiFindAllResponses
    @GetMapping
    public ResponseEntity<StaffMemberPageResponseDto> findAllStaffMember(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StaffMemberStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = StaffMemberSort.DEFAULT_PARAMETER) String sort) {
        var members = staffMemberService.listStaffMembers(q, status, page, size, StaffMemberSort.fromParameter(sort));
        var counts = staffMemberService.getServiceCounts(members.getContent().stream().map(StaffMember::getId).toList());
        var data = staffMemberMapper.toResponseDtoList(members.getContent());
        data.forEach(member -> member.setServiceCount(counts.getOrDefault(member.getId(), 0L)));
        return ResponseEntity.ok(new StaffMemberPageResponseDto(
                data,
                new StaffMemberPageResponseDto.PageInfo(members.getNumber(), members.getSize(),
                        members.getTotalElements(), members.getTotalPages())));
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

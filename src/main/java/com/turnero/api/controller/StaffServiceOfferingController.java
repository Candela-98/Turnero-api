package com.turnero.api.controller;

import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.StaffServiceOfferingRequestDto;
import com.turnero.api.openapi.ApiFindAllResponses;
import com.turnero.api.openapi.ApiUpdateResponses;
import com.turnero.api.service.StaffServiceOfferingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff-members")
@Tag(
        name = "Staff Service Offerings",
        description = "Endpoints para gestionar servicios asociados a profesionales"
)
public class StaffServiceOfferingController {
    private final StaffServiceOfferingService staffServiceOfferingService;

    public StaffServiceOfferingController(StaffServiceOfferingService staffServiceOfferingService) {
        this.staffServiceOfferingService = staffServiceOfferingService;
    }

    @Operation(
            summary = "Listar servicios asociados a un profesional",
            description = "Obtiene los servicios ofrecidos asociados a un profesional específico"
    )
    @ApiFindAllResponses
    @GetMapping("/{staff_member_id}/service-offerings")
    public ResponseEntity<List<ServOfferingResponseDto>> getServiceOfferings(@PathVariable("staff_member_id") Long staffMemberId) {

        var serviceOfferings = staffServiceOfferingService.getServiceOfferings(staffMemberId);
        return ResponseEntity.ok(serviceOfferings);
    }

    @Operation(
            summary = "Reemplazar servicios asociados a un profesional",
            description = "Reemplaza todas las asociaciones de servicios ofrecidos para un profesional específico"
    )
    @ApiUpdateResponses
    @PutMapping("/{staff_member_id}/service-offerings")
    public ResponseEntity<Void> replaceServiceOfferings(@PathVariable("staff_member_id") Long staffMemberId,
            @Valid @RequestBody StaffServiceOfferingRequestDto request) {

        staffServiceOfferingService.replaceServiceOfferings(staffMemberId, request.getServiceOfferingIds());
        return ResponseEntity.noContent().build();
    }
}

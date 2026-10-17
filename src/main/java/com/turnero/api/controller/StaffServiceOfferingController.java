package com.turnero.api.controller;

import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.StaffServiceOfferingRequestDto;
import com.turnero.api.openapi.ApiFindAllResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
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
            description = "Devuelve un array de servicios asociados, incluidos los inactivos conservados. Excluye servicios eliminados del catálogo."
    )
    @ApiFindAllResponses
    @ApiResponse(responseCode = "404", description = "Profesional no disponible en el negocio")
    @GetMapping("/{staff_member_id}/service-offerings")
    public ResponseEntity<List<ServOfferingResponseDto>> getServiceOfferings(@PathVariable("staff_member_id") Long staffMemberId) {

        var serviceOfferings = staffServiceOfferingService.getServiceOfferings(staffMemberId);
        return ResponseEntity.ok(serviceOfferings);
    }

    @Operation(
            summary = "Reemplazar servicios asociados a un profesional",
            description = "Reemplazo atómico. Una lista vacía quita todas las asociaciones; los duplicados se normalizan. Permite conservar inactivos existentes, pero no asignar inactivos nuevos. No modifica turnos existentes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Asociaciones actualizadas", content = @Content),
            @ApiResponse(responseCode = "400", description = "Lista ausente o IDs nulos/no positivos"),
            @ApiResponse(responseCode = "404", description = "Profesional o servicio no disponible en el negocio"),
            @ApiResponse(responseCode = "409", description = "Un servicio inactivo no puede asignarse por primera vez")
    })
    @PutMapping("/{staff_member_id}/service-offerings")
    public ResponseEntity<Void> replaceServiceOfferings(@PathVariable("staff_member_id") Long staffMemberId,
            @Valid @RequestBody StaffServiceOfferingRequestDto request) {

        staffServiceOfferingService.replaceServiceOfferings(staffMemberId, request.getServiceOfferingIds());
        return ResponseEntity.noContent().build();
    }
}

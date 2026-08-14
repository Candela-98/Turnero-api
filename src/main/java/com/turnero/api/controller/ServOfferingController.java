package com.turnero.api.controller;

import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.openapi.*;
import com.turnero.api.service.ServOfferingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Service Offerings",
        description = "Endpoints para gestionar servicios ofrecidos"
)
@RestController
@RequestMapping("/api/v1/service-offerings")
public class ServOfferingController {

    private final ServOfferingService servOfferingService;
    private final ServiceOfferingMapper serviceOfferingMapper;

    public ServOfferingController(ServOfferingService servOfferingService, ServiceOfferingMapper serviceOfferingMapper) {
        this.servOfferingService = servOfferingService;
        this.serviceOfferingMapper = serviceOfferingMapper;
    }

    @Operation(
            summary = "Crear servicio ofrecido",
            description = "Crea un nuevo servicio ofrecido en el sistema"
    )
    @ApiCreateResponses
    @PostMapping
    public ResponseEntity<ServOfferingResponseDto> saveServiceOffering(@Valid @RequestBody ServOfferingRequestDto servOfferingDto) {
        var serviceOffering = serviceOfferingMapper.toEntity(servOfferingDto);
        var serviceOfferingSaved = servOfferingService.saveServiceOffering(serviceOffering);
        var serviceOfferingResponseDto = serviceOfferingMapper.toResponseDto(serviceOfferingSaved);

        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingResponseDto);
    }

    @Operation(
            summary = "Obtener servicio ofrecido por ID",
            description = "Obtiene los detalles de un servicio ofrecido específico utilizando su ID"
    )
    @ApiFindByIdResponses
    @GetMapping("/{id}")
    public ResponseEntity<ServOfferingResponseDto> findServiceOffering(@PathVariable Long id) {
        var serviceOffering = servOfferingService.findServiceOffering(id);
        var serviceOfferingResponseDto = serviceOfferingMapper.toResponseDto(serviceOffering);
        return ResponseEntity.ok(serviceOfferingResponseDto);
    }

    @Operation(
            summary = "Actualizar servicio ofrecido",
            description = "Actualiza los detalles de un servicio ofrecido específico utilizando su ID"
    )
    @ApiUpdateResponses
    @PatchMapping("/{id}")
    public ResponseEntity<ServOfferingResponseDto> updateServOffering(@Valid @RequestBody ServOfferingUpdateRequestDto servOfferingDto, @PathVariable Long id) {
        var updatedServiceOffering = servOfferingService.updateServOffering(servOfferingDto, id);
        var responseDto = serviceOfferingMapper.toResponseDto(updatedServiceOffering);

        return ResponseEntity.ok(responseDto);
    }

    @Operation(
            summary = "Listar servicios ofrecidos",
            description = "Obtiene una lista de todos los servicios ofrecidos disponibles en el sistema"
    )
    @ApiFindAllResponses
    @GetMapping
    public ResponseEntity<List<ServOfferingResponseDto>> findAllServOffering() {
        var servOfferings = servOfferingService.findAllServOffering();
        var servOfferingResponseDtos = serviceOfferingMapper.toResponseDtoList(servOfferings);

        return ResponseEntity.ok(servOfferingResponseDtos);
    }

    @Operation(
            summary = "Eliminar servicio ofrecido",
            description = "Elimina un servicio ofrecido específico utilizando su ID"
    )
    @ApiDeleteResponses
    @DeleteMapping("/{id}")
    public ResponseEntity<ServiceOffering> deleteServOffering(@PathVariable Long id) {
        servOfferingService.deleteServOffering(id);
        return ResponseEntity.noContent().build();
    }
}

package com.turnero.api.controller;

import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.service.ServOfferingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-offerings")
public class ServOfferingController {

    private final ServOfferingService servOfferingService;
    private final ServiceOfferingMapper serviceOfferingMapper;

    public ServOfferingController(ServOfferingService servOfferingService, ServiceOfferingMapper serviceOfferingMapper) {
        this.servOfferingService = servOfferingService;
        this.serviceOfferingMapper = serviceOfferingMapper;
    }

    @PostMapping
    public ResponseEntity<ServiceOffering> saveServiceOffering(@Valid @RequestBody ServOfferingRequestDto servOfferingDto) {
        var serviceOffering = serviceOfferingMapper.toEntity(servOfferingDto);
        var serviceOfferingSaved = servOfferingService.saveServiceOffering(serviceOffering);
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingSaved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOffering> findServiceOffering(@PathVariable Long id) {
        var serviceOffering = servOfferingService.findServiceOffering(id);
        return ResponseEntity.ok(serviceOffering);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceOffering> updateServOffering(@Valid @RequestBody ServOfferingRequestDto servOfferingDto, @PathVariable Long id) {
        var serviceOfferingOffering = serviceOfferingMapper.toEntity(servOfferingDto);
        servOfferingService.updateServOffering(serviceOfferingOffering, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ServiceOffering>> findAllServOffering() {
        var servOfferings = servOfferingService.findAllServOffering();
        return ResponseEntity.ok(servOfferings);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ServiceOffering> deleteServOffering(@PathVariable Long id) {
        servOfferingService.deleteServOffering(id);
        return ResponseEntity.noContent().build();
    }
}

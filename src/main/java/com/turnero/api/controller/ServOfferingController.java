package com.turnero.api.controller;

import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.service.ServOfferingService;
import jakarta.validation.Valid;
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
        servOfferingService.saveServiceOffering(serviceOffering);
        return ResponseEntity.ok(serviceOffering);
    }

    @GetMapping("/{id}")
    public ServiceOffering findServiceOffering(@PathVariable Long id) {
        return servOfferingService.findServiceOffering(id);
    }

    @PutMapping("/{id}")
    public void updateServOffering(@Valid @RequestBody ServOfferingRequestDto servOfferingDto, @PathVariable Long id) {
        var servicioOffering = serviceOfferingMapper.toEntity(servOfferingDto);
        servOfferingService.updateServOffering(servicioOffering, id);
    }

    @GetMapping
    public List<ServiceOffering> findAllServOffering() {
        return servOfferingService.findAllServOffering();
    }

    @DeleteMapping("/{id}")
    public void deleteServOffering(@PathVariable Long id) {
        servOfferingService.deleteServOffering(id);
    }
}

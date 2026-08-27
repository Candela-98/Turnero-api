package com.turnero.api.controller;

import com.turnero.api.dto.BusinessHoursListResponseDto;
import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.mapper.BusinessHoursMapper;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.service.BusinessHoursService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business-hours")
@RequiredArgsConstructor
@Tag(name = "Business Hours", description = "Endpoints para configurar los horarios generales del negocio actual")
public class BusinessHoursController {
    private final BusinessHoursService businessHoursService;
    private final BusinessHoursMapper businessHoursMapper;

    @GetMapping
    @Operation(summary = "Obtener horarios semanales del negocio")
    public ResponseEntity<BusinessHoursListResponseDto> getBusinessHours() {
        return ResponseEntity.ok(responseFor(businessHoursService.getCurrentBusinessHours()));
    }

    @PutMapping
    @Operation(summary = "Reemplazar horarios semanales del negocio")
    public ResponseEntity<BusinessHoursListResponseDto> replaceBusinessHours(
            @Valid @RequestBody BusinessHoursReplaceRequestDto request
    ) {
        return ResponseEntity.ok(responseFor(businessHoursService.replaceCurrentBusinessHours(request)));
    }

    private BusinessHoursListResponseDto responseFor(List<BusinessHours> hours) {
        return BusinessHoursListResponseDto.builder()
                .data(businessHoursMapper.toResponseDtoList(hours))
                .build();
    }
}

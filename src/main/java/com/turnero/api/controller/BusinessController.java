package com.turnero.api.controller;

import com.turnero.api.dto.BusinessResponseDto;
import com.turnero.api.dto.BusinessUpdateRequestDto;
import com.turnero.api.mapper.BusinessMapper;
import com.turnero.api.service.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
public class BusinessController {
    private final BusinessService businessService;
    private final BusinessMapper businessMapper;

    @GetMapping
    public ResponseEntity<BusinessResponseDto> getBusiness() {
        return ResponseEntity.ok(businessMapper.toResponseDto(businessService.getCurrentBusiness()));
    }

    @PatchMapping
    public ResponseEntity<BusinessResponseDto> updateBusiness(@Valid @RequestBody BusinessUpdateRequestDto request) {
        return ResponseEntity.ok(businessMapper.toResponseDto(businessService.updateCurrentBusiness(request)));
    }
}

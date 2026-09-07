package com.turnero.api.controller;

import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;
import com.turnero.api.service.PublicBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/businesses")
@RequiredArgsConstructor
public class PublicBookingController {

    private final PublicBookingService publicBookingService;

    @GetMapping("/{businessSlug}/booking-profile")
    public ResponseEntity<PublicBookingProfileResponseDto> getBookingProfile(@PathVariable String businessSlug) {
        return ResponseEntity.ok(publicBookingService.getPublicBookingProfile(businessSlug));
    }

    @GetMapping("/{businessSlug}/services")
    public ResponseEntity<PublicServiceOfferingListResponseDto> getServices(@PathVariable String businessSlug) {
        return ResponseEntity.ok(publicBookingService.getPublicServices(businessSlug));
    }
}

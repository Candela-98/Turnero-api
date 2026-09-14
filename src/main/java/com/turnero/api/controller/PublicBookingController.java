package com.turnero.api.controller;

import com.turnero.api.dto.PublicAvailabilitySlotResponseDto;
import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;
import com.turnero.api.service.PublicBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/{businessSlug}/availability")
    public ResponseEntity<List<PublicAvailabilitySlotResponseDto>> getAvailability(@PathVariable String businessSlug,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(name = "service_offering_id") Long serviceOfferingId,
            @RequestParam(name = "staff_member_id") String staffMemberId) {

        return ResponseEntity.ok(publicBookingService.getPublicAvailability(businessSlug, from, to, serviceOfferingId,
                        staffMemberId));
    }
}

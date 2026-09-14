package com.turnero.api.service;

import com.turnero.api.dto.PublicAvailabilitySlotResponseDto;
import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;

import java.time.LocalDate;
import java.util.List;

public interface PublicBookingService {

    PublicBookingProfileResponseDto getPublicBookingProfile(String businessSlug);

    PublicServiceOfferingListResponseDto getPublicServices(String businessSlug);

    List<PublicAvailabilitySlotResponseDto> getPublicAvailability(String businessSlug, LocalDate from,
            LocalDate to, Long serviceOfferingId, String staffMemberId);
}

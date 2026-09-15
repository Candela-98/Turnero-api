package com.turnero.api.service;

import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;

public interface PublicBookingService {

    PublicBookingProfileResponseDto getPublicBookingProfile(String businessSlug);

    PublicServiceOfferingListResponseDto getPublicServices(String businessSlug);
}

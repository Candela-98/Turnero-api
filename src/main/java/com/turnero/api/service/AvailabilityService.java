package com.turnero.api.service;

import com.turnero.api.dto.AvailabilitySlotResponseDto;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityService {

    List<AvailabilitySlotResponseDto> getAvailableSlots(
            LocalDate from,
            LocalDate to,
            Long serviceOfferingId,
            Long staffMemberId,
            Long excludeAppointmentId
    );
}

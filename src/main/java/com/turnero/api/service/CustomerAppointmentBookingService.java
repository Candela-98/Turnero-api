package com.turnero.api.service;

import com.turnero.api.dto.CustomerAppointmentRequestDto;
import com.turnero.api.dto.PublicAppointmentResponseDto;

public interface CustomerAppointmentBookingService {
    PublicAppointmentResponseDto createCustomerAppointment(String slug, String token,
                                                           CustomerAppointmentRequestDto request);
}

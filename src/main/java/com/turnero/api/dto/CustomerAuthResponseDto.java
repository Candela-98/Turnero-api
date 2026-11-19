package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.CustomerStatus;

public record CustomerAuthResponseDto(Profile customer, AuthBusinessResponseDto business) {
    public record Profile(Long id, String name, String email,
                          @JsonProperty("phone_number") String phoneNumber, CustomerStatus status,
                          @JsonProperty("public_booking_blocked") boolean publicBookingBlocked) {}
}

package com.turnero.api.dto;

public record AuthSessionResponseDto(AuthUserResponseDto user, AuthBusinessResponseDto business) {
}

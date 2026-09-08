package com.turnero.api.service;

import com.turnero.api.dto.AuthSessionResponseDto;

public record AuthLoginResult(String sessionToken, AuthSessionResponseDto session) {
}

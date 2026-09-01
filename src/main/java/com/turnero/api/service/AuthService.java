package com.turnero.api.service;

import com.turnero.api.dto.AuthMeResponseDto;

public interface AuthService {

    String loginWithGoogle(String idToken, String ipAddress, String userAgent);

    AuthMeResponseDto getCurrentUser(String rawSessionToken);
}

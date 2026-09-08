package com.turnero.api.service;

import com.turnero.api.dto.AuthSessionResponseDto;

public interface AuthService {

    AuthLoginResult loginWithGoogle(String idToken, String ipAddress, String userAgent);

    AuthSessionResponseDto getCurrentUser(String rawSessionToken);

    void logout(String sessionToken);
}

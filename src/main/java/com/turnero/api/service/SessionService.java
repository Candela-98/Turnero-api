package com.turnero.api.service;

import com.turnero.api.model.UserSession;

public interface SessionService {

    String createSession(Long userId, String ipAddress, String userAgent);

    UserSession validateSession(String rawToken);

    void revokeSession(String rawToken);
}

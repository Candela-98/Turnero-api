package com.turnero.api.service;

import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.SessionScope;

public interface SessionService {

    String createSession(Long userId, SessionScope scope, String ipAddress, String userAgent);

    String createCustomerSession(Long userId, Long customerId, String ipAddress, String userAgent);

    UserSession validateSession(String rawToken, SessionScope expectedScope);

    void revokeSession(String rawToken, SessionScope expectedScope);
}

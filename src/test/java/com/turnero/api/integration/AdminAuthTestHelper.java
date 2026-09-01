package com.turnero.api.integration;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.model.User;
import com.turnero.api.model.enums.AuthProvider;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.SessionService;
import jakarta.servlet.http.Cookie;

import java.time.LocalDateTime;
import java.util.UUID;

class AdminAuthTestHelper {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final SessionProperties sessionProperties;

    AdminAuthTestHelper(
            UserRepository userRepository,
            SessionService sessionService,
            SessionProperties sessionProperties
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    Cookie ownerSessionCookie(Long businessId) {
        String suffix = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        User owner = userRepository.save(User.builder()
                .businessId(businessId)
                .name("Test Owner")
                .email("owner-" + suffix + "@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .authSubject("test-owner-" + suffix)
                .role(UserRole.OWNER)
                .createdAt(now)
                .updatedAt(now)
                .build());

        String rawToken = sessionService.createSession(owner.getId(), "127.0.0.1", "MockMvc");

        return new Cookie(sessionProperties.getCookieName(), rawToken);
    }
}

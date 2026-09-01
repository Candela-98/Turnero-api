package com.turnero.api.service;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.UserSession;
import com.turnero.api.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService{

    private final SessionProperties sessionProperties;
    private final UserSessionRepository userSessionRepository;

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private String generateToken(){
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        }catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public String createSession(Long userId, String ipAddress, String userAgent) {
        String rawToken = generateToken();
        String hashedToken = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();

        UserSession userSession = UserSession.builder()
                .userId(userId)
                .sessionTokenHash(hashedToken)
                .createdAt(now)
                .expiresAt(now.plusDays(sessionProperties.getTtlDays()))
                .lastSeenAt(now)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        userSessionRepository.save(userSession);

        return rawToken;
    }

    @Override
    public UserSession validateSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Session token is required");
        }

        String tokenHash = hashToken(rawToken);

        UserSession session = userSessionRepository
                .findBySessionTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));

        validateActiveSession(session);

        return session;
    }

    @Override
    public void revokeSession(String rawToken) {
        UserSession session = validateSession(rawToken);

        session.setRevokedAt(LocalDateTime.now());

        userSessionRepository.save(session);
    }

    private void validateActiveSession(UserSession session) {
        if (session.getRevokedAt() != null) {
            throw new UnauthorizedException("Session has been revoked");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Session has expired");
        }
    }
}

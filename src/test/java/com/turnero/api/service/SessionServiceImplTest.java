package com.turnero.api.service;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.UserSession;
import com.turnero.api.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private SessionProperties sessionProperties;

    @InjectMocks
    private SessionServiceImpl sessionService;

    @Test
    void createSession_createsAndSavesUserSessionWithHashedToken() {
        Long userId = 1L;
        String ipAddress = "127.0.0.1";
        String userAgent = "Mozilla/5.0";
        LocalDateTime beforeCreate = LocalDateTime.now();
        given(sessionProperties.getTtlDays()).willReturn(7L);

        String rawToken = sessionService.createSession(userId, ipAddress, userAgent);

        LocalDateTime afterCreate = LocalDateTime.now();
        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(captor.capture());
        UserSession savedSession = captor.getValue();

        assertThat(rawToken).isNotBlank();
        assertThat(savedSession.getSessionTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedSession.getSessionTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(savedSession.getUserId()).isEqualTo(userId);
        assertThat(savedSession.getIpAddress()).isEqualTo(ipAddress);
        assertThat(savedSession.getUserAgent()).isEqualTo(userAgent);
        assertThat(savedSession.getCreatedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(savedSession.getLastSeenAt()).isEqualTo(savedSession.getCreatedAt());
        assertThat(savedSession.getExpiresAt()).isEqualTo(savedSession.getCreatedAt().plusDays(7));
    }

    @Test
    void validateSession_whenSessionIsValid_returnsSession() {
        String rawToken = "valid-token";
        UserSession session = UserSession.builder()
                .sessionTokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        given(userSessionRepository.findBySessionTokenHash(sha256(rawToken))).willReturn(Optional.of(session));

        UserSession result = sessionService.validateSession(rawToken);

        assertThat(result).isSameAs(session);
        verify(userSessionRepository).findBySessionTokenHash(sha256(rawToken));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void validateSession_whenTokenIsNullOrBlank_throwsUnauthorized(String rawToken) {
        assertThatThrownBy(() -> sessionService.validateSession(rawToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session token is required");

        verify(userSessionRepository, never()).findBySessionTokenHash(anyString());
    }

    @Test
    void validateSession_whenTokenHashDoesNotExist_throwsUnauthorized() {
        String rawToken = "missing-token";
        given(userSessionRepository.findBySessionTokenHash(sha256(rawToken))).willReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.validateSession(rawToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid session");

        verify(userSessionRepository).findBySessionTokenHash(sha256(rawToken));
    }

    @Test
    void validateSession_whenSessionIsExpired_throwsUnauthorized() {
        String rawToken = "expired-token";
        UserSession session = UserSession.builder()
                .sessionTokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        given(userSessionRepository.findBySessionTokenHash(sha256(rawToken))).willReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.validateSession(rawToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session has expired");

        verify(userSessionRepository).findBySessionTokenHash(sha256(rawToken));
    }

    @Test
    void validateSession_whenSessionIsRevoked_throwsUnauthorized() {
        String rawToken = "revoked-token";
        UserSession session = UserSession.builder()
                .sessionTokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(LocalDateTime.now())
                .build();
        given(userSessionRepository.findBySessionTokenHash(sha256(rawToken))).willReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.validateSession(rawToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session has been revoked");

        verify(userSessionRepository).findBySessionTokenHash(sha256(rawToken));
    }

    private String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

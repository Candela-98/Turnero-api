package com.turnero.api.controller;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.AuthSessionResponseDto;
import com.turnero.api.dto.GoogleLoginRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.service.AuthLoginResult;
import com.turnero.api.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionProperties sessionProperties;

    @PostMapping("/google")
    public ResponseEntity<AuthSessionResponseDto> loginWithGoogle(@Valid @RequestBody GoogleLoginRequestDto requestDto, HttpServletRequest request) {

        AuthLoginResult loginResult = authService.loginWithGoogle(
                requestDto.idToken(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );

        ResponseCookie cookie = sessionCookie(loginResult.sessionToken(), Duration.ofDays(sessionProperties.getTtlDays()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(loginResult.session());
    }

    @GetMapping("/me")
    public ResponseEntity<AuthSessionResponseDto> me(HttpServletRequest request) {
        String sessionToken = extractRequiredSessionToken(request);

        return ResponseEntity.ok(
                authService.getCurrentUser(sessionToken)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        extractOptionalSessionToken(request).ifPresent(sessionToken -> {
            try {
                authService.logout(sessionToken);
            } catch (UnauthorizedException ignored) {
                // Logout is idempotent when the server session has already expired or been revoked.
            }
        });

        ResponseCookie expiredCookie = sessionCookie("", Duration.ZERO);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private String extractRequiredSessionToken(HttpServletRequest request) {
        return extractOptionalSessionToken(request)
                .orElseThrow(() -> new UnauthorizedException("Session token is required"));
    }

    private Optional<String> extractOptionalSessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> sessionProperties
                        .getCookieName()
                        .equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie
                .from(sessionProperties.getCookieName(), value)
                .httpOnly(true)
                .secure(sessionProperties.isSecure())
                .sameSite(sessionProperties.getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}

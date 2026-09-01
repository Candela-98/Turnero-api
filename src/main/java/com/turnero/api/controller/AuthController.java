package com.turnero.api.controller;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.AuthMeResponseDto;
import com.turnero.api.dto.GoogleLoginRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.service.AuthService;
import com.turnero.api.service.SessionService;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionProperties sessionProperties;

    @PostMapping("/google")
    public ResponseEntity<Void> loginWithGoogle(@Valid @RequestBody GoogleLoginRequestDto requestDto, HttpServletRequest request) {

        String sessionToken = authService.loginWithGoogle(
                requestDto.idToken(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );

        ResponseCookie cookie = ResponseCookie
                .from(sessionProperties.getCookieName(), sessionToken)
                .httpOnly(true)
                .secure(sessionProperties.isSecure())
                .sameSite(sessionProperties.getSameSite())
                .path("/")
                .maxAge(Duration.ofDays(sessionProperties.getTtlDays()))
                .build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthMeResponseDto> me(HttpServletRequest request) {
        String sessionToken = extractSessionToken(request);

        return ResponseEntity.ok(
                authService.getCurrentUser(sessionToken)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String sessionToken = extractSessionToken(request);

        authService.logout(sessionToken);

        ResponseCookie expiredCookie = ResponseCookie
                .from(sessionProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(sessionProperties.isSecure())
                .sameSite(sessionProperties.getSameSite())
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private String extractSessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new UnauthorizedException("Session token is required");
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> sessionProperties
                        .getCookieName()
                        .equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .filter(value -> !value.isBlank())
                .orElseThrow(() ->
                        new UnauthorizedException("Session token is required")
                );
    }
}

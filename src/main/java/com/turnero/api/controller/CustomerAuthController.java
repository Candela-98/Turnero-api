package com.turnero.api.controller;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.CustomerAuthResponseDto;
import com.turnero.api.dto.GoogleLoginRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.service.CustomerAuthService;
import com.turnero.api.auth.CustomerSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/customer/businesses/{slug}/auth")
@RequiredArgsConstructor
public class CustomerAuthController {
    private final CustomerAuthService auth;
    private final SessionProperties properties;
    private final CustomerSessionCookie sessionCookie;

    @PostMapping("/google")
    public ResponseEntity<CustomerAuthResponseDto> login(@PathVariable String slug,
            @Valid @RequestBody GoogleLoginRequestDto body, HttpServletRequest request) {
        var login = auth.login(slug, body.idToken(), request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                        cookie(login.token(), Duration.ofDays(properties.getTtlDays())).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(login.response());
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerAuthResponseDto> me(@PathVariable String slug, HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(auth.me(slug, sessionCookie.requiredToken(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@PathVariable String slug, HttpServletRequest request) {
        try {
            auth.logout(slug, sessionCookie.requiredToken(request));
        } catch (UnauthorizedException ignored) {
            // Missing, expired and revoked client sessions have idempotent logout.
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCustomerCookieName(), value).httpOnly(true)
                .secure(properties.isSecure()).sameSite(properties.getSameSite()).path("/").maxAge(maxAge).build();
    }
}

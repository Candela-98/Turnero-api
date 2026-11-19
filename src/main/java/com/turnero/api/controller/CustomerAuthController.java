package com.turnero.api.controller;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.CustomerAuthResponseDto;
import com.turnero.api.dto.GoogleLoginRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.service.CustomerAuthService;
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
@RequestMapping("/api/v1/customer/businesses/{slug}/auth")
@RequiredArgsConstructor
public class CustomerAuthController {
    private final CustomerAuthService auth;
    private final SessionProperties properties;

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
                .body(auth.me(slug, token(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@PathVariable String slug, HttpServletRequest request) {
        try {
            auth.logout(slug, token(request));
        } catch (UnauthorizedException ignored) {
            // Missing, expired and revoked client sessions have idempotent logout.
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) throw new UnauthorizedException("Session token is required");
        return Arrays.stream(request.getCookies())
                .filter(c -> properties.getCustomerCookieName().equals(c.getName()))
                .map(Cookie::getValue).filter(value -> !value.isBlank()).findFirst()
                .orElseThrow(() -> new UnauthorizedException("Session token is required"));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCustomerCookieName(), value).httpOnly(true)
                .secure(properties.isSecure()).sameSite(properties.getSameSite()).path("/").maxAge(maxAge).build();
    }
}

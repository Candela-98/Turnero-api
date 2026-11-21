package com.turnero.api.auth;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.exception.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class CustomerSessionCookie {
    private final SessionProperties properties;

    public String requiredToken(HttpServletRequest request) {
        if (request.getCookies() == null) throw new UnauthorizedException("Session token is required");
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getCustomerCookieName().equals(cookie.getName()))
                .map(Cookie::getValue).filter(value -> !value.isBlank()).findFirst()
                .orElseThrow(() -> new UnauthorizedException("Session token is required"));
    }
}

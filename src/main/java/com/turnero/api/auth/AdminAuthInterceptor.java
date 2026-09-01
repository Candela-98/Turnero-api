package com.turnero.api.auth;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.User;
import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final SessionProperties sessionProperties;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final AuthenticatedUserContext authenticatedUserContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawToken = extractSessionToken(request);
        UserSession session = sessionService.validateSession(rawToken);

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (user.getBusinessId() == null) {
            throw new ForbiddenException("User is not associated with a business");
        }

        if (user.getRole() != UserRole.OWNER) {
            throw new ForbiddenException("User is not allowed to access admin endpoints");
        }

        authenticatedUserContext.setAuthenticatedUser(user);

        return true;
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

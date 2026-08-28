package com.turnero.api.auth;

import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.User;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Component
@RequestScope
public class AuthenticatedUserContext {

    private User authenticatedUser;

    public void setAuthenticatedUser(User authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    public Optional<User> getAuthenticatedUser() {
        return Optional.ofNullable(authenticatedUser);
    }

    public User getRequiredUser() {
        return getAuthenticatedUser()
                .orElseThrow(() -> new UnauthorizedException("Authenticated user is required"));
    }
}

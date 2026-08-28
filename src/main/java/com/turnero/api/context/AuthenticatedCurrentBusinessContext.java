package com.turnero.api.context;

import com.turnero.api.auth.AuthenticatedUserContext;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCurrentBusinessContext implements CurrentBusinessContext{

    private final AuthenticatedUserContext authenticatedUserContext;

    public AuthenticatedCurrentBusinessContext(
            AuthenticatedUserContext authenticatedUserContext) {
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @Override
    public Long getCurrentBusinessId() {
        return authenticatedUserContext
                .getRequiredUser()
                .getBusinessId();
    }
}

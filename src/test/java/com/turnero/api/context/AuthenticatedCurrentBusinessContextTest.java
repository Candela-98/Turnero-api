package com.turnero.api.context;


import com.turnero.api.auth.AuthenticatedUserContext;
import com.turnero.api.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthenticatedCurrentBusinessContextTest {
    @Test
    void getCurrentBusinessId_returnsBusinessIdFromAuthenticatedUser() {
        AuthenticatedUserContext authenticatedUserContext =
                mock(AuthenticatedUserContext.class);

        User user = new User();
        user.setBusinessId(7L);

        when(authenticatedUserContext.getRequiredUser()).thenReturn(user);

        AuthenticatedCurrentBusinessContext context =
                new AuthenticatedCurrentBusinessContext(authenticatedUserContext);

        Long businessId = context.getCurrentBusinessId();

        assertThat(businessId).isEqualTo(7L);
    }
}

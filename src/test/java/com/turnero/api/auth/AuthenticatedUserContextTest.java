package com.turnero.api.auth;

import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.User;
import com.turnero.api.model.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserContextTest {

    @Test
    void setAuthenticatedUser_storesAndReturnsUser() {
        AuthenticatedUserContext context = new AuthenticatedUserContext();
        User user = user();

        context.setAuthenticatedUser(user);

        assertThat(context.getAuthenticatedUser()).containsSame(user);
    }

    @Test
    void getRequiredUser_whenUserExists_returnsUser() {
        AuthenticatedUserContext context = new AuthenticatedUserContext();
        User user = user();

        context.setAuthenticatedUser(user);

        assertThat(context.getRequiredUser()).isSameAs(user);
    }

    @Test
    void getRequiredUser_whenUserDoesNotExist_throwsUnauthorized() {
        AuthenticatedUserContext context = new AuthenticatedUserContext();

        assertThatThrownBy(context::getRequiredUser)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authenticated user is required");
    }

    private User user() {
        return User.builder()
                .id(1L)
                .businessId(10L)
                .role(UserRole.OWNER)
                .build();
    }
}

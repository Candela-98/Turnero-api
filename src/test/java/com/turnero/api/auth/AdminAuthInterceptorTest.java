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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private SessionProperties sessionProperties;

    @Mock
    private SessionService sessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticatedUserContext authenticatedUserContext;

    @InjectMocks
    private AdminAuthInterceptor interceptor;

    @Test
    void preHandle_whenRequestHasNoCookies_throwsUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session token is required");

        verifyNoInteractions(sessionService, userRepository, authenticatedUserContext);
    }

    @Test
    void preHandle_whenCookiesDoNotContainSessionCookie_throwsUnauthorized() {
        MockHttpServletRequest request = requestWithCookies(new Cookie("other_session", "raw-token"));
        given(sessionProperties.getCookieName()).willReturn("turnero_session");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session token is required");

        verifyNoInteractions(sessionService, userRepository, authenticatedUserContext);
    }

    @Test
    void preHandle_whenSessionCookieIsEmpty_throwsUnauthorized() {
        MockHttpServletRequest request = requestWithCookies(new Cookie("turnero_session", ""));
        given(sessionProperties.getCookieName()).willReturn("turnero_session");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session token is required");

        verifyNoInteractions(sessionService, userRepository, authenticatedUserContext);
    }

    @Test
    void preHandle_whenSessionValidationThrowsUnauthorized_propagatesUnauthorized() {
        String rawToken = "invalid-token";
        UnauthorizedException exception = new UnauthorizedException("Invalid session");
        MockHttpServletRequest request = requestWithSessionCookie(rawToken);
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionService.validateSession(rawToken)).willThrow(exception);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isSameAs(exception);

        then(sessionService).should().validateSession(rawToken);
        verifyNoInteractions(userRepository, authenticatedUserContext);
    }

    @Test
    void preHandle_whenSessionIsValidButUserDoesNotExist_throwsUnauthorized() {
        String rawToken = "valid-token";
        MockHttpServletRequest request = requestWithSessionCookie(rawToken);
        UserSession session = UserSession.builder().userId(1L).build();
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionService.validateSession(rawToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User not found");

        then(sessionService).should().validateSession(rawToken);
        then(userRepository).should().findById(1L);
        verifyNoInteractions(authenticatedUserContext);
    }

    @Test
    void preHandle_whenUserHasNoBusinessId_throwsForbidden() {
        String rawToken = "valid-token";
        MockHttpServletRequest request = requestWithSessionCookie(rawToken);
        UserSession session = UserSession.builder().userId(1L).build();
        User user = user(1L, null, UserRole.OWNER);
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionService.validateSession(rawToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not associated with a business");

        verifyNoInteractions(authenticatedUserContext);
    }

    @Test
    void preHandle_whenUserRoleIsNotOwner_throwsForbidden() {
        String rawToken = "valid-token";
        MockHttpServletRequest request = requestWithSessionCookie(rawToken);
        UserSession session = UserSession.builder().userId(1L).build();
        User user = user(1L, 10L, UserRole.ADMIN);
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionService.validateSession(rawToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not allowed to access admin endpoints");

        verifyNoInteractions(authenticatedUserContext);
    }

    @Test
    void preHandle_whenUserIsOwner_returnsTrueAndStoresUser() {
        String rawToken = "valid-token";
        MockHttpServletRequest request = requestWithSessionCookie(rawToken);
        UserSession session = UserSession.builder().userId(1L).build();
        User user = user(1L, 10L, UserRole.OWNER);
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionService.validateSession(rawToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        then(authenticatedUserContext).should().setAuthenticatedUser(user);
    }

    private MockHttpServletRequest requestWithSessionCookie(String value) {
        return requestWithCookies(new Cookie("turnero_session", value));
    }

    private MockHttpServletRequest requestWithCookies(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        return request;
    }

    private User user(Long id, Long businessId, UserRole role) {
        return User.builder()
                .id(id)
                .businessId(businessId)
                .role(role)
                .build();
    }
}

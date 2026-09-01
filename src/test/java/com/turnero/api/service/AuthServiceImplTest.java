package com.turnero.api.service;

import com.turnero.api.dto.AuthMeResponseDto;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.Business;
import com.turnero.api.model.User;
import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.AuthProvider;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private GoogleIdentityService googleIdentityService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private BusinessRepository businessRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginWithGoogle_whenLoginIsValid_createsSessionAndReturnsToken() {
        String idToken = "valid-id-token";
        String ipAddress = "127.0.0.1";
        String userAgent = "Mozilla/5.0";
        GoogleIdentityDto identity = new GoogleIdentityDto("google-subject", "user@example.com", true);
        User user = user(1L, 10L);
        given(googleIdentityService.verify(idToken)).willReturn(identity);
        given(userRepository.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, "google-subject")).willReturn(Optional.of(user));
        given(sessionService.createSession(1L, ipAddress, userAgent)).willReturn("generated-session-token");

        String result = authService.loginWithGoogle(idToken, ipAddress, userAgent);

        assertThat(result).isEqualTo("generated-session-token");
        verify(googleIdentityService).verify(idToken);
        verify(userRepository).findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, "google-subject");
        verify(sessionService).createSession(1L, ipAddress, userAgent);
    }

    @Test
    void loginWithGoogle_whenUserDoesNotExist_throwsUnauthorizedWithoutCreatingSession() {
        String idToken = "valid-id-token";
        GoogleIdentityDto identity = new GoogleIdentityDto("missing-subject", "user@example.com", true);
        given(googleIdentityService.verify(idToken)).willReturn(identity);
        given(userRepository.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, "missing-subject")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithGoogle(idToken, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User is not authorized");

        verify(userRepository).findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, "missing-subject");
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginWithGoogle_whenUserHasNoBusiness_throwsForbiddenWithoutCreatingSession() {
        String idToken = "valid-id-token";
        GoogleIdentityDto identity = new GoogleIdentityDto("google-subject", "user@example.com", true);
        User user = user(1L, null);
        given(googleIdentityService.verify(idToken)).willReturn(identity);
        given(userRepository.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, "google-subject"))
                .willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginWithGoogle(idToken, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not associated with a business");

        verify(userRepository).findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, "google-subject");
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginWithGoogle_whenGoogleIdentityVerificationFails_propagatesUnauthorized() {
        String idToken = "invalid-id-token";
        UnauthorizedException unauthorizedException = new UnauthorizedException("Invalid Google ID token");
        given(googleIdentityService.verify(idToken)).willThrow(unauthorizedException);

        assertThatThrownBy(() -> authService.loginWithGoogle(idToken, "127.0.0.1", "Mozilla/5.0"))
                .isSameAs(unauthorizedException);

        verify(googleIdentityService).verify(idToken);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(sessionService);
    }

    @Test
    void getCurrentUser_whenSessionUserAndBusinessAreValid_returnsCurrentUser() {
        String rawSessionToken = "raw-session-token";
        UserSession session = UserSession.builder()
                .userId(1L)
                .build();
        User user = User.builder()
                .id(1L)
                .businessId(10L)
                .name("Juan Perez")
                .email("juan@example.com")
                .role(UserRole.ADMIN)
                .build();
        Business business = Business.builder()
                .id(10L)
                .name("Barber Studio")
                .slug("barber-studio")
                .build();
        given(sessionService.validateSession(rawSessionToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(businessRepository.findById(10L)).willReturn(Optional.of(business));

        AuthMeResponseDto result = authService.getCurrentUser(rawSessionToken);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Juan Perez");
        assertThat(result.email()).isEqualTo("juan@example.com");
        assertThat(result.role()).isEqualTo(UserRole.ADMIN);
        assertThat(result.businessId()).isEqualTo(10L);
        assertThat(result.businessName()).isEqualTo("Barber Studio");
        assertThat(result.businessSlug()).isEqualTo("barber-studio");
        verify(sessionService).validateSession(rawSessionToken);
        verify(userRepository).findById(1L);
        verify(businessRepository).findById(10L);
    }

    @Test
    void getCurrentUser_whenSessionValidationFails_propagatesUnauthorized() {
        String rawSessionToken = "invalid-session-token";
        UnauthorizedException unauthorizedException = new UnauthorizedException("Invalid session");
        given(sessionService.validateSession(rawSessionToken)).willThrow(unauthorizedException);

        assertThatThrownBy(() -> authService.getCurrentUser(rawSessionToken))
                .isSameAs(unauthorizedException);

        verify(sessionService).validateSession(rawSessionToken);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(businessRepository);
    }

    @Test
    void getCurrentUser_whenUserDoesNotExist_throwsUnauthorizedWithoutLookingUpBusiness() {
        String rawSessionToken = "raw-session-token";
        UserSession session = UserSession.builder()
                .userId(1L)
                .build();
        given(sessionService.validateSession(rawSessionToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(rawSessionToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User not found");

        verify(sessionService).validateSession(rawSessionToken);
        verify(userRepository).findById(1L);
        verifyNoInteractions(businessRepository);
    }

    @Test
    void getCurrentUser_whenUserHasNoBusiness_throwsForbiddenWithoutLookingUpBusiness() {
        String rawSessionToken = "raw-session-token";
        UserSession session = UserSession.builder()
                .userId(1L)
                .build();
        User user = user(1L, null);
        given(sessionService.validateSession(rawSessionToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.getCurrentUser(rawSessionToken))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not associated with a business");

        verify(sessionService).validateSession(rawSessionToken);
        verify(userRepository).findById(1L);
        verifyNoInteractions(businessRepository);
    }

    @Test
    void getCurrentUser_whenBusinessDoesNotExist_throwsForbidden() {
        String rawSessionToken = "raw-session-token";
        UserSession session = UserSession.builder()
                .userId(1L)
                .build();
        User user = user(1L, 10L);
        given(sessionService.validateSession(rawSessionToken)).willReturn(session);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(businessRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(rawSessionToken))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Business not found");

        verify(sessionService).validateSession(rawSessionToken);
        verify(userRepository).findById(1L);
        verify(businessRepository).findById(10L);
    }

    @Test
    void logout_delegatesToSessionServiceRevokeSession() {
        String sessionToken = "raw-session-token";

        authService.logout(sessionToken);

        verify(sessionService).revokeSession(sessionToken);
        verifyNoInteractions(googleIdentityService, userRepository, businessRepository);
    }

    private User user(Long id, Long businessId) {
        return User.builder()
                .id(id)
                .businessId(businessId)
                .build();
    }
}

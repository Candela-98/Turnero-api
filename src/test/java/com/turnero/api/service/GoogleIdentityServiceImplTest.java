package com.turnero.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoogleIdentityServiceImplTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    @Mock
    private GoogleIdToken googleIdToken;

    private GoogleIdentityServiceImpl googleIdentityService;

    @BeforeEach
    void setUp() {
        googleIdentityService = new GoogleIdentityServiceImpl(verifier);
    }

    @Test
    void verify_whenTokenIsValidAndEmailIsVerified_returnsGoogleIdentity() throws Exception {
        String idToken = "valid-token";
        GoogleIdToken.Payload payload = payload("google-subject", "user@example.com", true);
        given(verifier.verify(idToken)).willReturn(googleIdToken);
        given(googleIdToken.getPayload()).willReturn(payload);

        GoogleIdentityDto result = googleIdentityService.verify(idToken);

        assertThat(result.subject()).isEqualTo("google-subject");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.emailVerified()).isTrue();
        verify(verifier).verify(idToken);
    }

    @Test
    void verify_whenTokenCannotBeVerified_throwsUnauthorized() throws Exception {
        String idToken = "invalid-token";
        given(verifier.verify(idToken)).willReturn(null);

        assertThatThrownBy(() -> googleIdentityService.verify(idToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google ID token");

        verify(verifier).verify(idToken);
    }

    @Test
    void verify_whenEmailIsNotVerified_throwsUnauthorized() throws Exception {
        String idToken = "unverified-email-token";
        GoogleIdToken.Payload payload = payload("google-subject", "user@example.com", false);
        given(verifier.verify(idToken)).willReturn(googleIdToken);
        given(googleIdToken.getPayload()).willReturn(payload);

        assertThatThrownBy(() -> googleIdentityService.verify(idToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google email is not verified");

        verify(verifier).verify(idToken);
    }

    @Test
    void verify_whenVerifierThrowsGeneralSecurityException_throwsUnauthorized() throws Exception {
        String idToken = "security-error-token";
        given(verifier.verify(idToken)).willThrow(new GeneralSecurityException("signature error"));

        assertThatThrownBy(() -> googleIdentityService.verify(idToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google ID token");

        verify(verifier).verify(idToken);
    }

    @Test
    void verify_whenVerifierThrowsIOException_throwsUnauthorized() throws Exception {
        String idToken = "io-error-token";
        given(verifier.verify(idToken)).willThrow(new IOException("transport error"));

        assertThatThrownBy(() -> googleIdentityService.verify(idToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google ID token");

        verify(verifier).verify(idToken);
    }

    private GoogleIdToken.Payload payload(String subject, String email, boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        return payload;
    }
}

package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.AuthBusinessResponseDto;
import com.turnero.api.dto.AuthSessionResponseDto;
import com.turnero.api.dto.AuthUserResponseDto;
import com.turnero.api.dto.GoogleLoginRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.service.AuthService;
import com.turnero.api.service.AuthLoginResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionProperties sessionProperties;
    @MockitoBean
    private AdminAuthInterceptor adminAuthInterceptor;

    private static final String GOOGLE_LOGIN_URL = "/api/v1/auth/google";
    private static final String ME_URL = "/api/v1/auth/me";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    @Test
    void loginWithGoogle_whenRequestIsValid_returnsOkWithSessionCookie() throws Exception {
        String idToken = "valid-id-token";
        String sessionToken = "generated-session-token";
        String ipAddress = "203.0.113.10";
        String userAgent = "Mozilla/5.0";

        given(authService.loginWithGoogle(idToken, ipAddress, userAgent))
                .willReturn(new AuthLoginResult(sessionToken, authSession()));
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionProperties.isSecure()).willReturn(true);
        given(sessionProperties.getSameSite()).willReturn("Strict");
        given(sessionProperties.getTtlDays()).willReturn(7L);

        MvcResult result = mockMvc.perform(post(GOOGLE_LOGIN_URL)
                        .with(request -> {
                            request.setRemoteAddr(ipAddress);
                            return request;
                        })
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequestDto(idToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.business.id").value(10))
                .andExpect(jsonPath("$.business.onboarding_status").value("READY"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("turnero_session=" + sessionToken);
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=604800");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(sessionToken);

        then(authService).should().loginWithGoogle(idToken, ipAddress, userAgent);
    }

    @Test
    void loginWithGoogle_whenIdTokenIsEmpty_returns400() throws Exception {
        mockMvc.perform(post(GOOGLE_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequestDto(""))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("idToken"))
                .andExpect(jsonPath("$.details[0].message").value("ID token is required"))
                .andExpect(jsonPath("$.path").value(GOOGLE_LOGIN_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void loginWithGoogle_whenIdTokenIsMissing_returns400() throws Exception {
        mockMvc.perform(post(GOOGLE_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("idToken"))
                .andExpect(jsonPath("$.details[0].message").value("ID token is required"))
                .andExpect(jsonPath("$.path").value(GOOGLE_LOGIN_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void loginWithGoogle_whenUserIsNotAllowed_returns403WithoutCookie() throws Exception {
        String idToken = "valid-id-token";
        given(authService.loginWithGoogle(idToken, "127.0.0.1", null))
                .willThrow(new ForbiddenException("User is not allowed to access admin endpoints"));

        mockMvc.perform(post(GOOGLE_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"valid-id-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("User is not allowed to access admin endpoints"))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());
    }

    @Test
    void me_whenSessionCookieIsValid_returnsCurrentUser() throws Exception {
        String sessionToken = "raw-session-token";
        AuthSessionResponseDto response = authSession();
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(authService.getCurrentUser(sessionToken)).willReturn(response);

        mockMvc.perform(get(ME_URL)
                        .cookie(
                                new Cookie("other_session", "wrong-token"),
                                new Cookie("turnero_session", sessionToken)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.name").value("Juan Perez"))
                .andExpect(jsonPath("$.user.email").value("juan@example.com"))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.business.id").value(10))
                .andExpect(jsonPath("$.business.name").value("Barber Studio"))
                .andExpect(jsonPath("$.business.slug").value("barber-studio"))
                .andExpect(jsonPath("$.business.onboarding_status").value("READY"));

        then(authService).should().getCurrentUser(sessionToken);
    }

    @Test
    void me_whenRequestHasNoCookies_returns401WithoutCallingAuthService() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Session token is required"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(ME_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void me_whenCookiesDoNotContainSessionCookie_returns401WithoutCallingAuthService() throws Exception {
        given(sessionProperties.getCookieName()).willReturn("turnero_session");

        mockMvc.perform(get(ME_URL)
                        .cookie(new Cookie("other_session", "wrong-token"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Session token is required"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(ME_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void me_whenSessionCookieIsEmpty_returns401WithoutCallingAuthService() throws Exception {
        given(sessionProperties.getCookieName()).willReturn("turnero_session");

        mockMvc.perform(get(ME_URL)
                        .cookie(new Cookie("turnero_session", ""))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Session token is required"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(ME_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void me_whenAuthServiceThrowsUnauthorized_returns401() throws Exception {
        String sessionToken = "invalid-session-token";
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(authService.getCurrentUser(sessionToken))
                .willThrow(new UnauthorizedException("Invalid session"));

        mockMvc.perform(get(ME_URL)
                        .cookie(new Cookie("turnero_session", sessionToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid session"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(ME_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).should().getCurrentUser(sessionToken);
    }

    @Test
    void me_whenSessionUserIsNotOwner_returns403() throws Exception {
        String sessionToken = "valid-session-token";
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(authService.getCurrentUser(sessionToken))
                .willThrow(new ForbiddenException("User is not allowed to access admin endpoints"));

        mockMvc.perform(get(ME_URL).cookie(new Cookie("turnero_session", sessionToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void logout_whenSessionCookieIsValid_returns204AndExpiresSessionCookie() throws Exception {
        String sessionToken = "raw-session-token";
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionProperties.isSecure()).willReturn(true);
        given(sessionProperties.getSameSite()).willReturn("Strict");

        MvcResult result = mockMvc.perform(post(LOGOUT_URL)
                        .cookie(
                                new Cookie("other_session", "wrong-token"),
                                new Cookie("turnero_session", sessionToken)
                        ))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("turnero_session=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/");

        then(authService).should().logout(sessionToken);
    }

    @Test
    void logout_whenSessionCookieIsValidAndSecureIsDisabled_expiresCookieWithoutSecureAttribute() throws Exception {
        String sessionToken = "raw-session-token";
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionProperties.isSecure()).willReturn(false);
        given(sessionProperties.getSameSite()).willReturn("Lax");

        MvcResult result = mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("turnero_session", sessionToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("turnero_session=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).doesNotContain("Secure");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/");

        then(authService).should().logout(sessionToken);
    }

    @Test
    void logout_whenRequestHasNoCookies_returns204AndExpiresCookie() throws Exception {
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionProperties.isSecure()).willReturn(false);
        given(sessionProperties.getSameSite()).willReturn("Lax");

        mockMvc.perform(post(LOGOUT_URL)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void logout_whenCookiesDoNotContainSessionCookie_returns204WithoutCallingAuthService() throws Exception {
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        given(sessionProperties.isSecure()).willReturn(false);
        given(sessionProperties.getSameSite()).willReturn("Lax");

        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("other_session", "wrong-token"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void logout_whenAuthServiceThrowsUnauthorized_returns204() throws Exception {
        String sessionToken = "invalid-session-token";
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        willThrow(new UnauthorizedException("Invalid session"))
                .given(authService)
                .logout(sessionToken);

        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("turnero_session", sessionToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        then(authService).should().logout(sessionToken);
    }

    private AuthSessionResponseDto authSession() {
        return new AuthSessionResponseDto(
                new AuthUserResponseDto(1L, "Juan Perez", "juan@example.com", UserRole.OWNER, "https://example.com/avatar.png"),
                new AuthBusinessResponseDto(10L, "Barber Studio", "barber-studio", BusinessOnboardingStatus.READY)
        );
    }
}

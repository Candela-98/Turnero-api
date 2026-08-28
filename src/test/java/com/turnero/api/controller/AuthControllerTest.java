package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.AuthMeResponseDto;
import com.turnero.api.dto.GoogleLoginRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.service.AuthService;
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

        given(authService.loginWithGoogle(idToken, ipAddress, userAgent)).willReturn(sessionToken);
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
                .andExpect(content().string(""))
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
    void me_whenSessionCookieIsValid_returnsCurrentUser() throws Exception {
        String sessionToken = "raw-session-token";
        AuthMeResponseDto response = new AuthMeResponseDto(
                1L,
                "Juan Perez",
                "juan@example.com",
                UserRole.ADMIN,
                10L,
                "Barber Studio",
                "barber-studio"
        );
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
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Juan Perez"))
                .andExpect(jsonPath("$.email").value("juan@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.businessId").value(10))
                .andExpect(jsonPath("$.businessName").value("Barber Studio"))
                .andExpect(jsonPath("$.businessSlug").value("barber-studio"));

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
    void logout_whenRequestHasNoCookies_returns401WithoutCallingAuthService() throws Exception {
        mockMvc.perform(post(LOGOUT_URL)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Session token is required"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(LOGOUT_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void logout_whenCookiesDoNotContainSessionCookie_returns401WithoutCallingAuthService() throws Exception {
        given(sessionProperties.getCookieName()).willReturn("turnero_session");

        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("other_session", "wrong-token"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Session token is required"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(LOGOUT_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void logout_whenAuthServiceThrowsUnauthorized_returns401() throws Exception {
        String sessionToken = "invalid-session-token";
        given(sessionProperties.getCookieName()).willReturn("turnero_session");
        willThrow(new UnauthorizedException("Invalid session"))
                .given(authService)
                .logout(sessionToken);

        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("turnero_session", sessionToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid session"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(LOGOUT_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(authService).should().logout(sessionToken);
    }
}

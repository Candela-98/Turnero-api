package com.turnero.api.exception;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizedErrorAttributesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/public/cancellations/secret",
            "/api/v1/public/./cancellations/secret",
            "/api/v1/public/x/../cancellations/secret",
            "/api/v1/public/%2e/cancellations/secret",
            "/api/v1/public/x/%2E%2E/cancellations/secret",
            "/api/v1/public/%252e/cancellations/secret",
            "/api/v1/public/cancellations/secret/../../other",
            "/context/api/v1/public/./cancellations/secret"
    })
    void servletFallbackSanitizesOnlyPathAndPreservesStatus(String path) {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        var attributes = new SanitizedErrorAttributes().getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());
        assertThat(attributes.get("status")).isEqualTo(404);
        assertThat(attributes.get("path")).isEqualTo("/api/v1/public/cancellations/{token}");
        assertThat(attributes.toString()).doesNotContain("secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/public/%xx/secret", "/api/v1/public/../secret"})
    void ambiguousFallbackPathsAreNotEchoed(String path) {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        var attributes = new SanitizedErrorAttributes().getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());
        assertThat(attributes.get("status")).isEqualTo(400);
        assertThat(attributes.get("path")).isEqualTo("/{redacted}");
    }
}

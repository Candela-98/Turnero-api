package com.turnero.api.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class ErrorPathSanitizerTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/public/cancellations/secret",
            "/api/v1/public/cancellations/secret/extra?token=secret",
            "/api/v1/public/cancellations/%73ecret",
            "/api/v1/public/./cancellations/secret",
            "/api/v1/public/x/../cancellations/secret",
            "/api/v1/public/%2e/cancellations/secret",
            "/api/v1/public/x/%2E%2E/cancellations/secret",
            "/api/v1/public/%252e/cancellations/secret",
            "/api/v1/public/cancellations/secret/../../other",
            "/api;id=secret/v1/public/cancellations/secret",
            "/api/v1/public/cancellations/secret%0Asecret",
            "/api/v1/public/cancellations;jsessionid=secret/secret",
            "/api/v1/public/cancellations/bad%escape",
            "/api/v1/public/%63ancellations/bad%escape"
    })
    void redactsCancellationSuffix(String path) {
        assertThat(ErrorResponse.builder().path(path).build().getPath())
                .isEqualTo("/api/v1/public/cancellations/{token}");
    }

    @Test
    void supportsContextPathsAndEncodedPrefixes() {
        assertThat(ErrorPathSanitizer.sanitize("/context/api/v1/public/cancellations/secret"))
                .isEqualTo("/api/v1/public/cancellations/{token}");
        assertThat(ErrorPathSanitizer.sanitize("/api/v1/public/%63ancellations/secret"))
                .isEqualTo("/api/v1/public/cancellations/{token}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/public/%xx/secret", "/api/v1/public/../secret",
            "/api/v1/public/%25252525252e/secret", "/unrelated/secret?token=secret"
    })
    void ambiguousPathsAreRedacted(String path) {
        assertThat(ErrorPathSanitizer.sanitize(path)).isEqualTo("/{redacted}");
    }
    @Test
    void preservesOtherPathsAndNull() {
        assertThat(ErrorPathSanitizer.sanitize("/api/v1/appointments/123"))
                .isEqualTo("/api/v1/appointments/123");
        assertThat(ErrorPathSanitizer.sanitize(null)).isNull();
    }

    @Test
    void genericErrorDoesNotLogSensitiveExceptionContent(
            org.springframework.boot.test.system.CapturedOutput output) {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/public/cancellations/secret-value");
        new GlobalExceptionHandler().handleGeneric(new RuntimeException("secret-value and cookie-value"), request);
        assertThat(output.getAll()).doesNotContain("secret-value", "cookie-value");
    }

    @Test
    void everyGlobalHandlerSanitizesItsErrorResponse() throws Exception {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/public/cancellations/must-not-appear");
        // Every handler accepting a simple message exception is exercised, including
        // 404, 409, auth, generic and 429. Body/type-validation are covered over HTTP.
        for (var method : GlobalExceptionHandler.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(org.springframework.web.bind.annotation.ExceptionHandler.class)) continue;
            Class<?> exceptionType = method.getParameterTypes()[0];
            Object exception;
            if (exceptionType == PublicRateLimitExceededException.class) {
                exception = new PublicRateLimitExceededException(3);
            } else if (exceptionType == PublicRateLimitStorageFullException.class) {
                exception = new PublicRateLimitStorageFullException();
            } else {
                var constructor = Arrays.stream(exceptionType.getConstructors())
                        .filter(c -> Arrays.equals(c.getParameterTypes(), new Class<?>[]{String.class}))
                        .findFirst();
                if (constructor.isEmpty()) continue;
                exception = constructor.get().newInstance("Existing message");
            }
            var response = (org.springframework.http.ResponseEntity<?>) method.invoke(handler, exception, request);
            assertThat(((ErrorResponse) response.getBody()).getPath())
                    .as(method.getName()).isEqualTo("/api/v1/public/cancellations/{token}");
        }
    }
}

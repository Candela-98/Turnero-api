package com.turnero.api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercise a real Tomcat ERROR dispatch, not a direct call to ErrorAttributes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicErrorFallbackHttpTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/public/cancellations/secret",
            "/api/v1/public/./cancellations/secret",
            "/api/v1/public/x/../cancellations/secret",
            "/api/v1/public/%2e/cancellations/secret",
            "/api/v1/public/x/%2e%2e/cancellations/secret"
    })
    void realErrorDispatchNeverEchoesCancellationToken(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Accept", "application/json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(404);
            var body = mapper.readTree(response.body());
            assertThat(body.path("status").asInt()).isEqualTo(404);
            assertThat(body.path("path").asText()).isEqualTo("/api/v1/public/cancellations/{token}");
            assertThat(response.body()).doesNotContain("secret");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        FilterRegistrationBean<Filter> errorDispatchProbe() {
            var registration = new FilterRegistrationBean<Filter>();
            registration.setFilter((request, response, chain) -> ((HttpServletResponse) response).sendError(404));
            registration.addUrlPatterns("/api/v1/public/*");
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            registration.setOrder(0);
            return registration;
        }
    }
}

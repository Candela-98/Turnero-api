package com.turnero.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {
    @Autowired MockMvc mockMvc;

    @Test
    void shouldGenerateRequestIdWhenHeaderIsMissing() throws Exception {
        var result = mockMvc.perform(get("/actuator/health")).andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldPreserveCanonicalUuid() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(get("/__test/request-id/log").header("X-Request-Id", id))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", id))
                .andExpect(content().string(id));
    }

    @Test
    void uppercaseUuidIsNormalized() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(get("/__test/request-id/log").header("X-Request-Id", id.toUpperCase(java.util.Locale.ROOT)))
                .andExpect(header().string("X-Request-Id", id)).andExpect(content().string(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel-secret-token", "turnero_session=private-cookie", "test-request-id",
            "1-1-1-1-1", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\nforged", " padded-identifier "})
    void arbitraryHeadersNeverReachMdcOrLogs(String unsafe, CapturedOutput output) throws Exception {
        var result = mockMvc.perform(get("/__test/request-id/log").header("X-Request-Id", unsafe))
                .andExpect(status().isOk()).andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(id);
        assertThat(output.getAll()).contains("[requestId=" + id + "]").doesNotContain(unsafe);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void oversizedAndMultipleHeadersAreReplaced() throws Exception {
        for (Object[] headers : new Object[][]{
                {"x".repeat(4096)}, {UUID.randomUUID().toString(), "private-second-value"}}) {
            var result = mockMvc.perform(get("/__test/request-id/log").header("X-Request-Id", headers))
                    .andExpect(status().isOk()).andReturn();
            String id = result.getResponse().getHeader("X-Request-Id");
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(id).isNotIn(headers);
            assertThat(result.getResponse().getContentAsString()).isEqualTo(id);
        }
    }

    @Test
    void errorLogsKeepSafeCorrelationAndMdcIsCleared(CapturedOutput output) throws Exception {
        var result = mockMvc.perform(get("/__test/request-id/error")
                        .header("X-Request-Id", "private-request-header"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(output.getAll()).contains("[requestId=" + id + "]", "Unexpected error occurred")
                .doesNotContain("private-request-header", "private-exception-content");
        assertThat(MDC.get("requestId")).isNull();
        var next = mockMvc.perform(get("/__test/request-id/log")).andReturn();
        assertThat(next.getResponse().getHeader("X-Request-Id")).isNotEqualTo(id);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean ProbeController requestIdProbeController() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/__test/request-id/log")
        String log() {
            LoggerFactory.getLogger(ProbeController.class).info("Request correlation probe");
            return MDC.get("requestId");
        }

        @GetMapping("/__test/request-id/error")
        String error() {
            throw new IllegalStateException("private-exception-content");
        }
    }
}

package com.turnero.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateRequestIdWhenHeaderIsMissing() throws Exception {

        var result = mockMvc.perform(get("/actuator/health"))
                .andReturn();

        String requestId =
                result.getResponse().getHeader("X-Request-Id");

        assertThat(requestId).isNotBlank();
    }

    @Test
    void shouldPreserveRequestIdWhenHeaderIsProvided() throws Exception {

        String expectedRequestId = "test-request-id";

        var result = mockMvc.perform(
                        get("/actuator/health")
                                .header("X-Request-Id", expectedRequestId))
                .andReturn();

        String requestId =
                result.getResponse().getHeader("X-Request-Id");

        assertThat(requestId).isEqualTo(expectedRequestId);
    }
}

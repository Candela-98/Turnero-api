package com.turnero.api.config;

import com.turnero.api.auth.AdminAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        WebMvcConfig.class,
        WebMvcConfigTest.TestController.class
})
class WebMvcConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthInterceptor adminAuthInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        given(adminAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
    }

    @Test
    void adminEndpoint_passesThroughAdminAuthInterceptor() throws Exception {
        mockMvc.perform(get("/api/v1/business"))
                .andExpect(status().isOk())
                .andExpect(content().string("business"));

        then(adminAuthInterceptor).should().preHandle(any(), any(), any());
    }

    @Test
    void authEndpoint_doesNotPassThroughAdminAuthInterceptor() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("auth"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void availabilityEndpoint_passesThroughAdminAuthInterceptor() throws Exception {
        mockMvc.perform(get("/api/v1/availability/slots"))
                .andExpect(status().isOk())
                .andExpect(content().string("availability"));

        then(adminAuthInterceptor).should().preHandle(any(), any(), any());
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/business")
        String business() {
            return "business";
        }

        @GetMapping("/api/v1/auth/me")
        String auth() {
            return "auth";
        }

        @GetMapping("/api/v1/availability/slots")
        String availability() {
            return "availability";
        }
    }
}

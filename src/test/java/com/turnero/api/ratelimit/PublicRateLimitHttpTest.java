package com.turnero.api.ratelimit;

import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.config.PublicRateLimitProperties;
import com.turnero.api.controller.PublicBookingController;
import com.turnero.api.controller.PublicCancellationController;
import com.turnero.api.exception.InvalidPublicTokenException;
import com.turnero.api.exception.PublicRateLimitStorageFullException;
import com.turnero.api.service.PublicBookingService;
import com.turnero.api.service.PublicCancellationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PublicBookingController.class, PublicCancellationController.class,
        com.turnero.api.controller.BusinessController.class}, properties = {
        "public-booking.rate-limit.enabled=true",
        "public-booking.rate-limit.read.capacity=3",
        "public-booking.rate-limit.read.refill-tokens=3",
        "public-booking.rate-limit.read.refill-period=1h",
        "public-booking.rate-limit.write.capacity=2",
        "public-booking.rate-limit.write.refill-tokens=2",
        "public-booking.rate-limit.write.refill-period=1h",
        "public-booking.rate-limit.business.read.capacity=2",
        "public-booking.rate-limit.business.read.refill-tokens=2",
        "public-booking.rate-limit.business.read.refill-period=1h",
        "public-booking.rate-limit.business.write.capacity=1",
        "public-booking.rate-limit.business.write.refill-tokens=1",
        "public-booking.rate-limit.business.write.refill-period=1h"
})
class PublicRateLimitHttpTest {
    private static final AtomicInteger CLIENTS = new AtomicInteger();
    @Autowired MockMvc mvc;
    @Autowired PublicRateLimitProperties properties;
    @MockitoBean PublicBookingService booking;
    @MockitoBean PublicCancellationService cancellation;
    @MockitoBean AdminAuthInterceptor admin;
    @MockitoBean com.turnero.api.service.BusinessService businessService;
    @MockitoBean com.turnero.api.mapper.BusinessMapper businessMapper;
    private String ip;

    @BeforeEach
    void setUp() {
        ip = "192.0.2." + CLIENTS.incrementAndGet();
        properties.setEnabled(true);
    }

    @AfterEach
    void restore() { properties.setEnabled(true); }

    private MockHttpServletRequestBuilder client(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> { request.setRemoteAddr(ip); return request; });
    }

    @Test
    void allBookingReadsShareBusinessQuotaAndRejectedCallsDoNotReachService() throws Exception {
        mvc.perform(client(get("/api/v1/public/businesses/a/booking-profile"))).andExpect(status().isOk());
        mvc.perform(client(get("/api/v1/public/businesses/a/services"))).andExpect(status().isOk());
        mvc.perform(client(get("/api/v1/public/businesses/a/availability")
                        .param("from", "2027-01-01").param("to", "2027-01-02")
                        .param("service_offering_id", "1").param("staff_member_id", "any")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1800"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.timestamp").exists());
        verify(booking, never()).getPublicAvailability(anyString(), any(), any(), any(), any());
        // A rejection by business a did not burn the remaining global permit.
        mvc.perform(client(get("/api/v1/public/businesses/b/services"))).andExpect(status().isOk());
        mvc.perform(client(get("/api/v1/public/businesses/c/services"))).andExpect(status().isTooManyRequests());
        verifyNoInteractions(admin);
    }

    @Test
    void cancellingAndCreatingShareWriteGlobalButReadIsIndependent() throws Exception {
        // Invalid bodies still spend admitted write permits before body validation.
        mvc.perform(client(post("/api/v1/public/businesses/a/appointments")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).andExpect(status().isBadRequest());
        mvc.perform(client(post("/api/v1/public/businesses/a/appointments")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).andExpect(status().isTooManyRequests());
        mvc.perform(client(post("/api/v1/public/cancellations/secret-one")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).andExpect(status().isOk());
        mvc.perform(client(post("/api/v1/public/cancellations/secret-two")
                .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"))
                .andExpect(content().string(not(containsString("secret-two"))));
        mvc.perform(client(get("/api/v1/public/businesses/a/services"))).andExpect(status().isOk());
        verify(booking, never()).createPublicAppointment(anyString(), any());
        verify(cancellation, times(1)).cancelPublicAppointment(anyString(), any());
    }

    @Test
    void rotatingTokensAndSpoofedHeadersDoNotRenewTheQuota() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(client(get("/api/v1/public/cancellations/token-" + i)
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .header("Forwarded", "for=198.51.100." + i)
                            .header("X-Real-IP", "198.51.100." + i)))
                    .andExpect(status().isOk());
        }
        mvc.perform(client(get("/api/v1/public/cancellations/different-token")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"));
        verify(cancellation, times(3)).getPublicCancellation(anyString());
        ip = "203.0.113.42";
        mvc.perform(client(get("/api/v1/public/cancellations/other-client"))).andExpect(status().isOk());
    }

    @Test
    void disabledBypassesBothScopes() throws Exception {
        properties.setEnabled(false);
        for (int i = 0; i < 8; i++) {
            mvc.perform(client(get("/api/v1/public/businesses/a/services"))).andExpect(status().isOk());
        }
        verify(booking, times(8)).getPublicServices("a");
    }

    @Test
    void headConsumesReadQuotaAndOptionsDoesNot() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(client(options("/api/v1/public/businesses/a/services"))).andExpect(status().isOk());
        }
        mvc.perform(client(head("/api/v1/public/businesses/a/services"))).andExpect(status().isOk());
        mvc.perform(client(get("/api/v1/public/businesses/a/services"))).andExpect(status().isOk());
        mvc.perform(client(head("/api/v1/public/businesses/a/services"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void tokenErrorsAndMalformedBodiesAreSanitizedWithoutChangingTheirContract() throws Exception {
        when(cancellation.getPublicCancellation("secret-token"))
                .thenThrow(new InvalidPublicTokenException("Invalid public cancellation token"));
        mvc.perform(client(get("/api/v1/public/cancellations/secret-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("Invalid public cancellation token"))
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"));
        mvc.perform(client(post("/api/v1/public/cancellations/body-token")
                .contentType(MediaType.APPLICATION_JSON).content("{")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"));
    }

    @Test
    void saturationUses503WithoutInventingRetryAfter() throws Exception {
        // Handler contract is distinct from an exhausted client quota.
        when(cancellation.getPublicCancellation("secret"))
                .thenThrow(new PublicRateLimitStorageFullException());
        mvc.perform(client(get("/api/v1/public/cancellations/secret")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_STORAGE_FULL"))
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"));
    }

    @Test
    void exhaustedPublicQuotaDoesNotAffectAdminEndpoints() throws Exception {
        when(admin.preHandle(any(), any(), any())).thenReturn(true);
        for (int i = 0; i < 3; i++) {
            mvc.perform(client(get("/api/v1/public/cancellations/token-" + i))).andExpect(status().isOk());
        }
        mvc.perform(client(get("/api/v1/public/cancellations/token-x"))).andExpect(status().isTooManyRequests());
        for (int i = 0; i < 5; i++) {
            mvc.perform(client(get("/api/v1/business"))).andExpect(status().isOk());
        }
        verify(admin, times(5)).preHandle(any(), any(), any());
        verify(businessService, times(5)).getCurrentBusiness();
    }

    @Test
    void clientResolverUsesOnlySocketPeer() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("Forwarded", "for=198.51.100.2");
        request.addHeader("X-Real-IP", "198.51.100.3");
        assertThat(new ClientIpResolver().resolve(request)).isEqualTo("192.0.2.10");
    }
}

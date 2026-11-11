package com.turnero.api.ratelimit;

import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.controller.PublicBookingController;
import com.turnero.api.controller.PublicCancellationController;
import com.turnero.api.service.PublicBookingService;
import com.turnero.api.service.PublicCancellationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PublicBookingController.class, PublicCancellationController.class}, properties = {
        "public-booking.rate-limit.enabled=true", "public-booking.rate-limit.max-buckets=2",
        "public-booking.rate-limit.read.capacity=2", "public-booking.rate-limit.read.refill-tokens=2",
        "public-booking.rate-limit.read.refill-period=1h",
        "public-booking.rate-limit.business.read.capacity=1",
        "public-booking.rate-limit.business.read.refill-tokens=1",
        "public-booking.rate-limit.business.read.refill-period=1h"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PublicRateLimitSaturationHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean PublicBookingService booking;
    @MockitoBean PublicCancellationService cancellation;
    @MockitoBean AdminAuthInterceptor admin;

    private MockHttpServletRequestBuilder client(String ip, String path) {
        return get(path).with(request -> { request.setRemoteAddr(ip); return request; });
    }

    @Test
    void fullRealStoreReturns503AndPreservesExistingQuotas() throws Exception {
        mvc.perform(client("192.0.2.1", "/api/v1/public/cancellations/first")).andExpect(status().isOk());
        mvc.perform(client("192.0.2.2", "/api/v1/public/cancellations/second")).andExpect(status().isOk());
        mvc.perform(client("192.0.2.3", "/api/v1/public/cancellations/private-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_STORAGE_FULL"))
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"));
        verify(cancellation, never()).getPublicCancellation("private-token");
        mvc.perform(client("192.0.2.1", "/api/v1/public/cancellations/another")).andExpect(status().isOk());
        var result = mvc.perform(client("192.0.2.1", "/api/v1/public/cancellations/exhausted-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.path").value("/api/v1/public/cancellations/{token}"))
                .andReturn();
        long seconds = Long.parseLong(result.getResponse().getHeader("Retry-After"));
        assertThat(seconds).isBetween(1L, 1800L);
        verify(cancellation, times(3)).getPublicCancellation(anyString());
        verifyNoInteractions(admin);
    }

    @Test
    void rejectingNewBusinessForLackOfSpaceDoesNotSpendGlobalQuota() throws Exception {
        mvc.perform(client("192.0.2.1", "/api/v1/public/businesses/a/services")).andExpect(status().isOk());
        mvc.perform(client("192.0.2.1", "/api/v1/public/businesses/b/services"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_STORAGE_FULL"));
        verify(booking, never()).getPublicServices("b");
        mvc.perform(client("192.0.2.1", "/api/v1/public/cancellations/remaining-global"))
                .andExpect(status().isOk());
        verify(cancellation).getPublicCancellation("remaining-global");
    }
}

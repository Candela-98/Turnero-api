package com.turnero.api.ratelimit;

import com.turnero.api.config.PublicRateLimitProperties;
import com.turnero.api.controller.PublicBookingController;
import com.turnero.api.controller.PublicCancellationController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;

@Component
public class PublicRateLimitInterceptor implements HandlerInterceptor {
    private static final Set<String> BOOKING_READ = Set.of(
            "/api/v1/public/businesses/{businessSlug}/booking-profile",
            "/api/v1/public/businesses/{businessSlug}/services",
            "/api/v1/public/businesses/{businessSlug}/availability");
    private static final String BOOKING_WRITE = "/api/v1/public/businesses/{businessSlug}/appointments";
    private static final String CANCELLATION = "/api/v1/public/cancellations/{token}";

    private final PublicRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final PublicRateLimitProperties properties;

    public PublicRateLimitInterceptor(PublicRateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                                      PublicRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        String pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        boolean write = "POST".equals(request.getMethod());
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        String slug = null;
        if (method.getBeanType() == PublicBookingController.class
                && ((read && BOOKING_READ.contains(pattern)) || (write && BOOKING_WRITE.equals(pattern)))) {
            Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (variables instanceof Map<?, ?> values && values.get("businessSlug") instanceof String value) {
                slug = value;
            } else {
                throw new IllegalStateException("Missing public booking scope");
            }
        } else if (!(method.getBeanType() == PublicCancellationController.class
                && CANCELLATION.equals(pattern) && (read || write))) {
            return true;
        }
        rateLimiter.check(clientIpResolver.resolve(request), write, slug);
        return true;
    }
}

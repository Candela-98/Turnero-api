package com.turnero.api.config;

import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.ratelimit.PublicRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Import({PublicRateLimitProperties.class, PublicRateLimitInterceptor.class,
        com.turnero.api.ratelimit.PublicRateLimiter.class,
        com.turnero.api.ratelimit.PublicRateLimitBucketStore.class,
        com.turnero.api.ratelimit.ClientIpResolver.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    private final PublicRateLimitInterceptor publicRateLimitInterceptor;

    public WebMvcConfig(
            AdminAuthInterceptor adminAuthInterceptor,
            PublicRateLimitInterceptor publicRateLimitInterceptor) {

        this.adminAuthInterceptor = adminAuthInterceptor;
        this.publicRateLimitInterceptor = publicRateLimitInterceptor;
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(
                        "/api/v1/business/**",
                        "/api/v1/booking-settings/**",
                        "/api/v1/business-hours/**",
                        "/api/v1/customers/**",
                        "/api/v1/service-offerings/**",
                        "/api/v1/staff-members/**",
                        "/api/v1/appointments/**",
                        "/api/v1/availability/**"
                );

        registry.addInterceptor(publicRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/public/businesses/*/booking-profile",
                        "/api/v1/public/businesses/*/services",
                        "/api/v1/public/businesses/*/availability",
                        "/api/v1/public/businesses/*/appointments",
                        "/api/v1/public/cancellations/*"
                );
    }
}

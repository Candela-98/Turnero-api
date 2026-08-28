package com.turnero.api.config;

import com.turnero.api.auth.AdminAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(
                        "/api/v1/business/**",
                        "/api/v1/booking-settings/**",
                        "/api/v1/customers/**",
                        "/api/v1/service-offerings/**",
                        "/api/v1/staff-members/**",
                        "/api/v1/appointments/**",
                        "/api/v1/availability/**"
                );
    }
}

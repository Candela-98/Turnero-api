package com.turnero.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class SessionCleanupTimeConfig {

    @Bean
    public Clock sessionCleanupClock() {
        return Clock.systemDefaultZone();
    }
}

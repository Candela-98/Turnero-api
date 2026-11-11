package com.turnero.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRateLimitPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });

    @Test
    void yamlBindsGlobalAndBusinessDefaultsAndDisablesForwardedHeaders() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var p = context.getBean(PublicRateLimitProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getRead().getCapacity()).isEqualTo(60);
            assertThat(p.getWrite().getCapacity()).isEqualTo(10);
            assertThat(p.getBusiness().getRead().getCapacity()).isEqualTo(30);
            assertThat(p.getBusiness().getWrite().getCapacity()).isEqualTo(5);
            assertThat(p.getBusiness().getRead().getRefillPeriod()).isEqualTo(java.time.Duration.ofMinutes(1));
            assertThat(p.getMaxBuckets()).isEqualTo(10_000);
            assertThat(context.getEnvironment().getProperty("server.forward-headers-strategy")).isEqualTo("none");
        });
    }

    @Test
    void environmentOverridesAreApplied() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("rateLimitEnvironment", Map.of(
                        "PUBLIC_BOOKING_RATE_LIMIT_ENABLED", "false",
                        "PUBLIC_BOOKING_RATE_LIMIT_BUSINESS_READ_CAPACITY", "12",
                        "PUBLIC_BOOKING_RATE_LIMIT_BUSINESS_WRITE_REFILL_PERIOD", "2m",
                        "PUBLIC_BOOKING_RATE_LIMIT_MAX_BUCKETS", "200",
                        "PUBLIC_BOOKING_RATE_LIMIT_IDLE_RETENTION", "20m"
                )))).run(context -> {
            assertThat(context).hasNotFailed();
            var p = context.getBean(PublicRateLimitProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.getBusiness().getRead().getCapacity()).isEqualTo(12);
            assertThat(p.getBusiness().getWrite().getRefillPeriod()).isEqualTo(java.time.Duration.ofMinutes(2));
            assertThat(p.getMaxBuckets()).isEqualTo(200);
            assertThat(p.getIdleRetention()).isEqualTo(java.time.Duration.ofMinutes(20));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "read.capacity=0", "write.refill-tokens=-1", "read.refill-period=0s",
            "business.read.capacity=0", "business.write.refill-period=-1s",
            "business.read.refill-period=999999999d", "max-buckets=1",
            "idle-retention=0s", "idle-retention=999999999d"
    })
    void invalidConfigurationFailsStartup(String value) {
        runner.withPropertyValues("public-booking.rate-limit." + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "write", "business.read", "business.write"})
    void unsupportedBucket4jRateFailsAtStartup(String scope) {
        runner.withPropertyValues(
                "public-booking.rate-limit." + scope + ".capacity=1",
                "public-booking.rate-limit." + scope + ".refill-tokens=2",
                "public-booking.rate-limit." + scope + ".refill-period=1ns").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class)
                    .hasStackTraceContaining("Bucket4j");
        });
    }

    @Test
    void bucket4jMaximumRateIsAccepted() {
        runner.withPropertyValues(
                "public-booking.rate-limit.business.read.capacity=1",
                "public-booking.rate-limit.business.read.refill-tokens=1",
                "public-booking.rate-limit.business.read.refill-period=1ns").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PublicRateLimitProperties.class)
                    .getBusiness().getRead().toBandwidth()).isNotNull();
        });
    }
    @Test
    void requiredGroupsCannotBeMissing() {
        new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PublicRateLimitProperties.class)
    static class Config {}
}

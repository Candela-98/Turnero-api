package com.turnero.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCleanupPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    private final ApplicationContextRunner yamlContextRunner = contextRunner.withInitializer(context -> {
        try {
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                    .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    });

    @Test
    void bindsDefaultsFromApplicationYaml() {
        yamlContextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SessionCleanupProperties.class);
            SessionCleanupProperties properties = context.getBean(SessionCleanupProperties.class);
            assertThat(properties.getRetentionDays()).isEqualTo(30);
            assertThat(properties.getBatchSize()).isEqualTo(500);
            assertThat(properties.getCron()).isEqualTo("0 0 3 * * *");
        });
    }

    @Test
    void bindsExplicitEnvironmentVariablesThroughYamlPlaceholders() {
        yamlContextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("cleanupTestEnvironment", Map.of(
                        "AUTH_SESSION_CLEANUP_RETENTION_DAYS", "45",
                        "AUTH_SESSION_CLEANUP_BATCH_SIZE", "250",
                        "AUTH_SESSION_CLEANUP_CRON", "0 30 2 * * *"
                )))).run(context -> {
            assertThat(context).hasNotFailed();
            SessionCleanupProperties properties = context.getBean(SessionCleanupProperties.class);
            assertThat(properties.getRetentionDays()).isEqualTo(45);
            assertThat(properties.getBatchSize()).isEqualTo(250);
            assertThat(properties.getCron()).isEqualTo("0 30 2 * * *");
        });
    }

    @ParameterizedTest
    @CsvSource({"retention-days, 0, retentionDays", "retention-days, -1, retentionDays",
            "batch-size, 0, batchSize", "batch-size, -1, batchSize"})
    void rejectsNonPositiveValues(String property, String value, String field) {
        yamlContextRunner.withPropertyValues("auth.session.cleanup." + property + "=" + value)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining(field);
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrBlankCron(String cron) {
        ApplicationContextRunner runner = contextRunner.withPropertyValues(
                "auth.session.cleanup.retention-days=30", "auth.session.cleanup.batch-size=500");
        if (cron != null) {
            runner = runner.withPropertyValues("auth.session.cleanup.cron=" + cron);
        }
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class)
                    .hasStackTraceContaining("cron");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SessionCleanupProperties.class)
    static class TestConfiguration {
    }
}

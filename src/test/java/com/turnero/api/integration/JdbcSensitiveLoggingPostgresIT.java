package com.turnero.api.integration;

import com.turnero.api.model.UserSession;
import com.turnero.api.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("prod")
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers
class JdbcSensitiveLoggingPostgresIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", postgres::getHost);
        registry.add("DB_PORT", postgres::getFirstMappedPort);
        registry.add("DB_NAME", postgres::getDatabaseName);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserSessionRepository sessions;

    @Test
    void postgresConstraintErrorDoesNotLogSessionHash(CapturedOutput output) {
        Long businessId = jdbc.queryForObject("""
                INSERT INTO businesses (name, slug, timezone, status, onboarding_status, created_at, updated_at)
                VALUES ('Logging QA', 'logging-qa', 'UTC', 'ACTIVE', 'READY', now(), now())
                RETURNING id
                """, Long.class);
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (business_id, name, email, auth_provider, auth_subject, role, created_at, updated_at)
                VALUES (?, 'Logging QA', 'logging-qa@example.invalid', 'GOOGLE', 'logging-qa', 'OWNER', now(), now())
                RETURNING id
                """, Long.class, businessId);
        String syntheticHash = "a1".repeat(32);
        LocalDateTime now = LocalDateTime.now();
        sessions.saveAndFlush(session(userId, syntheticHash, now));

        assertThatThrownBy(() -> sessions.saveAndFlush(session(userId, syntheticHash, now)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(output.getAll()).contains("SQLState: 23505");
        assertThat(output.getAll().contains(syntheticHash))
                .as("Hibernate JDBC error logger exposed the synthetic session hash").isFalse();
    }

    private UserSession session(Long userId, String hash, LocalDateTime now) {
        return UserSession.builder()
                .userId(userId)
                .sessionTokenHash(hash)
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(now.plusDays(1))
                .build();
    }
}

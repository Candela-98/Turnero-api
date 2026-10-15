package com.turnero.api.integration;

import com.turnero.api.TurneroApiApplication;
import com.turnero.api.config.SessionCleanupProperties;
import com.turnero.api.model.Business;
import com.turnero.api.model.User;
import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.AuthProvider;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.repository.UserSessionRepository;
import com.turnero.api.scheduler.SessionCleanupScheduler;
import com.turnero.api.service.SessionCleanupService;
import com.turnero.api.service.SessionCleanupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {SessionCleanupServiceImpl.class, SessionCleanupPostgresIT.CleanupTestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"auth.session.cleanup.retention-days=30", "auth.session.cleanup.batch-size=2"})
@ActiveProfiles("test")
@Testcontainers
class SessionCleanupPostgresIT {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);
    private static final LocalDateTime CUTOFF = NOW.minusDays(30);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero_test")
            .withUsername("turnero")
            .withPassword("turnero");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired private SessionCleanupService service;
    @Autowired private UserSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private Long userId;

    @BeforeEach
    void setUp() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(context.getBeansOfType(TurneroApiApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(SessionCleanupScheduler.class)).isEmpty();

        // This datasource belongs exclusively to this class's Testcontainers database.
        jdbcTemplate.update("DELETE FROM user_sessions");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM businesses");

        Long businessId = businessRepository.save(Business.builder()
                .name("Cleanup test business")
                .slug("cleanup-test")
                .timezone("America/Argentina/Buenos_Aires")
                .status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build()).getId();
        userId = userRepository.save(User.builder()
                .businessId(businessId)
                .name("Cleanup test user")
                .email("cleanup@example.test")
                .authProvider(AuthProvider.GOOGLE)
                .authSubject("cleanup-test-subject")
                .role(UserRole.OWNER)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build()).getId();
    }

    @Test
    void cleanupPreservesNonCandidatesAndIsIdempotentAcrossCommittedExecutions() {
        saveSession(NOW.plusDays(7), null);
        Long expiredId = saveSession(CUTOFF.minusSeconds(1), null);
        saveSession(NOW.minusDays(5), null);
        Long revokedId = saveSession(NOW.plusDays(7), CUTOFF.minusSeconds(1));
        saveSession(NOW.plusDays(7), NOW.minusDays(5));
        saveSession(CUTOFF, null);
        saveSession(CUTOFF.minusDays(10), CUTOFF);
        saveSession(CUTOFF.minusDays(10), NOW.minusDays(5));
        List<SessionState> initial = databaseSessions();
        assertThat(initial).hasSize(8);
        List<SessionState> retained = initial.stream()
                .filter(session -> !List.of(expiredId, revokedId).contains(session.id()))
                .toList();

        assertThat(cleanupOutsideTestTransaction()).isEqualTo(2);
        assertThat(databaseSessions()).containsExactlyElementsOf(retained);

        assertThat(cleanupOutsideTestTransaction()).isZero();
        assertThat(databaseSessions()).containsExactlyElementsOf(retained);
    }

    @Test
    void cleanupCommitsOneConfiguredBatchAndContinuesOnFollowingExecutions() {
        Long activeId = saveSession(NOW.plusDays(7), null);
        List<Long> candidateIds = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            candidateIds.add(saveSession(CUTOFF.minusDays(index + 1), null));
        }
        assertDatabaseIds(activeId, candidateIds.get(0), candidateIds.get(1),
                candidateIds.get(2), candidateIds.get(3), candidateIds.get(4));

        assertThat(cleanupOutsideTestTransaction()).isEqualTo(2);
        assertDatabaseIds(activeId, candidateIds.get(2), candidateIds.get(3), candidateIds.get(4));

        assertThat(cleanupOutsideTestTransaction()).isEqualTo(2);
        assertDatabaseIds(activeId, candidateIds.get(4));

        assertThat(cleanupOutsideTestTransaction()).isEqualTo(1);
        assertDatabaseIds(activeId);

        assertThat(cleanupOutsideTestTransaction()).isZero();
        assertDatabaseIds(activeId);
    }

    private int cleanupOutsideTestTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        int deletedCount = service.cleanupSessionsBatch();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return deletedCount;
    }

    private Long saveSession(LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return sessionRepository.save(UserSession.builder()
                .userId(userId)
                .sessionTokenHash(UUID.randomUUID().toString())
                .createdAt(CUTOFF.minusDays(60))
                .lastSeenAt(CUTOFF.minusDays(60))
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build()).getId();
    }

    private List<SessionState> databaseSessions() {
        return jdbcTemplate.query("SELECT id, expires_at, revoked_at FROM user_sessions ORDER BY id", (row, index) ->
                new SessionState(row.getLong("id"), row.getObject("expires_at", LocalDateTime.class),
                        row.getObject("revoked_at", LocalDateTime.class)));
    }

    private void assertDatabaseIds(Long... expected) {
        assertThat(databaseSessions()).extracting(SessionState::id).containsExactly(expected);
    }

    private record SessionState(Long id, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserSession.class)
    @EnableJpaRepositories(basePackageClasses = UserSessionRepository.class)
    @EnableConfigurationProperties(SessionCleanupProperties.class)
    static class CleanupTestConfiguration {

        @Bean
        Clock sessionCleanupClock() {
            return Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"),
                    ZoneId.of("America/Argentina/Buenos_Aires"));
        }
    }
}

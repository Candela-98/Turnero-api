package com.turnero.api.integration;

import com.turnero.api.TurneroApiApplication;
import com.turnero.api.config.PublicTokenCleanupProperties;
import com.turnero.api.dto.PublicCancellationRequestDto;
import com.turnero.api.exception.InvalidPublicTokenException;
import com.turnero.api.exception.PublicTokenExpiredException;
import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import com.turnero.api.scheduler.PublicTokenCleanupScheduler;
import com.turnero.api.scheduler.SessionCleanupScheduler;
import com.turnero.api.security.PublicTokenHasher;
import com.turnero.api.service.PublicCancellationService;
import com.turnero.api.service.PublicCancellationServiceImpl;
import com.turnero.api.service.PublicTokenCleanupService;
import com.turnero.api.service.PublicTokenCleanupServiceImpl;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = {PublicTokenCleanupServiceImpl.class, PublicCancellationServiceImpl.class,
        PublicTokenHasher.class, PublicTokenCleanupPostgresIT.CleanupTestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"public-token.cleanup.retention-days=30", "public-token.cleanup.batch-size=2"})
@ActiveProfiles("test")
@Testcontainers
@Timeout(60)
class PublicTokenCleanupPostgresIT {

    private static final Instant REFERENCE = Instant.parse("2026-10-19T01:30:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(REFERENCE, ZoneOffset.UTC);
    private static final LocalDateTime CUTOFF = NOW.minusDays(30);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero_public_token_test").withUsername("turnero").withPassword("turnero");

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
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "6");
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET lock_timeout = '20s'; SET statement_timeout = '25s'");
    }

    @Autowired private PublicTokenCleanupService cleanup;
    @Autowired private PublicCancellationService cancellation;
    @Autowired private PublicTokenHasher hasher;
    @Autowired private AppointmentPublicTokenRepository repository;
    @Autowired private PublicTokenCleanupProperties properties;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;
    @Autowired private SqlCapture sqlCapture;

    @BeforeEach
    void setUp() throws Exception {
        // Refuse fixture cleanup unless this is exactly this class's container datasource.
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
        }
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(cleanup)).isTrue();
        assertThat(AopUtils.isAopProxy(cancellation)).isTrue();
        assertThat(context.getBeansOfType(TurneroApiApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(PublicTokenCleanupScheduler.class)).isEmpty();
        assertThat(context.getBeansOfType(SessionCleanupScheduler.class)).isEmpty();
        jdbc.execute("TRUNCATE TABLE businesses RESTART IDENTITY CASCADE");
        properties.setRetentionDays(30);
        properties.setBatchSize(2);
    }

    @Test
    void repositorySelectsAndDeletesOnlyEligibleTokensWithStrictThirtyDayBoundaries() {
        BusinessFixture business = business("UTC");
        BusinessFixture other = business("UTC");
        Long expired = token(business, CUTOFF.minusSeconds(1), null).id();
        Long used = token(business, NOW.plusDays(7), CUTOFF.minusSeconds(1)).id();
        List<Long> retained = List.of(
                token(business, NOW.plusDays(7), null).id(),
                token(business, NOW.minusDays(5), null).id(),
                token(business, CUTOFF, null).id(),
                token(business, CUTOFF.minusDays(10), CUTOFF).id(),
                token(business, CUTOFF.minusDays(10), NOW.minusDays(5)).id(),
                token(other, CUTOFF.minusDays(1), null).id());

        assertThat(repository.findCleanupCandidateIds(business.id(), CUTOFF, PageRequest.of(0, 1)))
                .containsExactly(expired);
        assertThat(repository.findCleanupCandidateIds(business.id(), CUTOFF, PageRequest.of(0, 500)))
                .containsExactly(expired, used);
        List<Long> requested = new ArrayList<>(retained);
        requested.addAll(List.of(expired, used, Long.MAX_VALUE));
        assertThat(tx().<Integer>execute(status -> repository.deleteCleanupCandidatesByIds(business.id(), requested, CUTOFF)))
                .isEqualTo(2);
        assertThat(ids()).containsExactlyElementsOf(retained);
        assertThat(tx().<Integer>execute(status -> repository.deleteCleanupCandidatesByIds(business.id(), requested, CUTOFF)))
                .isZero();
        assertThat(tx().<Integer>execute(status -> repository.deleteCleanupCandidatesByIds(business.id(), List.of(), CUTOFF)))
                .isZero();
    }

    @Test
    void serviceCommitsActualDeletesAndPreservesRetainedTokensAcrossRepeatedExecutions() {
        BusinessFixture business = business("UTC");
        Long expired = token(business, CUTOFF.minusSeconds(1), null).id();
        Long used = token(business, NOW.plusDays(7), CUTOFF.minusSeconds(1)).id();
        Long active = token(business, NOW.plusDays(7), null).id();
        Long recent = token(business, NOW.minusDays(5), null).id();
        Long expirationBoundary = token(business, CUTOFF, null).id();
        Long useBoundary = token(business, CUTOFF.minusDays(10), CUTOFF).id();

        assertThat(cleanupOutsideTransaction()).isEqualTo(2);
        assertThat(ids()).doesNotContain(expired, used).containsExactly(active, recent, expirationBoundary, useBoundary);
        assertThat(cleanupOutsideTransaction()).isZero();
        assertThat(ids()).containsExactly(active, recent, expirationBoundary, useBoundary);
    }

    @Test
    void serviceAppliesEachBusinessTimezoneFromOneReferenceIncludingDateAndDstDifferences() {
        properties.setBatchSize(20);
        BusinessFixture west = business("America/Argentina/Buenos_Aires");
        BusinessFixture east = business("Asia/Tokyo");
        BusinessFixture dst = business("Australia/Sydney");
        LocalDateTime commonDate = LocalDateTime.of(2026, 9, 19, 1, 30);
        Long westUnused = token(west, commonDate, null).id();
        Long westUsed = token(west, CUTOFF.minusDays(5), commonDate).id();
        Long eastUnused = token(east, commonDate, null).id();
        Long eastUsed = token(east, NOW.plusDays(5), commonDate).id();
        // Oct 19 is UTC+11; Sep 19 is UTC+10. Subtract days in local time, not in UTC.
        LocalDateTime dstCutoff = LocalDateTime.of(2026, 9, 19, 12, 30);
        Long dstEligible = token(dst, dstCutoff.minusMinutes(30), null).id();
        Long dstBoundary = token(dst, dstCutoff, null).id();
        Long dstUsedBoundary = token(dst, dstCutoff.minusDays(1), dstCutoff).id();

        assertThat(cleanupOutsideTransaction()).isEqualTo(3);
        assertThat(ids()).doesNotContain(eastUnused, eastUsed, dstEligible)
                .containsExactly(westUnused, westUsed, dstBoundary, dstUsedBoundary);
    }

    @Test
    void serviceSharesConfiguredGlobalBudgetInBusinessIdAndTokenIdOrderAcrossCommittedBatches() {
        properties.setBatchSize(3);
        BusinessFixture first = business("UTC");
        BusinessFixture second = business("UTC");
        BusinessFixture third = business("UTC");
        Long secondFirst = token(second, CUTOFF.minusDays(1), null).id();
        Long firstFirst = token(first, CUTOFF.minusDays(1), null).id();
        Long firstNext = token(first, CUTOFF.minusDays(2), null).id();
        Long secondNext = token(second, CUTOFF.minusDays(2), null).id();
        Long thirdFirst = token(third, CUTOFF.minusDays(1), null).id();

        assertThat(cleanupOutsideTransaction()).isEqualTo(3);
        assertThat(ids()).doesNotContain(firstFirst, firstNext, secondFirst).containsExactly(secondNext, thirdFirst);
        assertThat(cleanupOutsideTransaction()).isEqualTo(2);
        assertThat(ids()).isEmpty();
        assertThat(cleanupOutsideTransaction()).isZero();
    }

    @Test
    void serviceHandlesNoBusinessesAndContinuesPastBusinessesWithoutCandidates() {
        assertThat(cleanupOutsideTransaction()).isZero();
        BusinessFixture empty = business("UTC");
        BusinessFixture next = business("UTC");
        Long active = token(empty, NOW.plusDays(7), null).id();
        token(next, CUTOFF.minusDays(1), null);

        assertThat(cleanupOutsideTransaction()).isEqualTo(1);
        assertThat(ids()).containsExactly(active);
        assertThat(cleanupOutsideTransaction()).isZero();
    }

    @Test
    void serviceRollsBackEarlierDeletesWhenLaterBusinessFails() {
        properties.setBatchSize(5);
        BusinessFixture first = business("UTC");
        business("invalid/test-zone");
        Long id = token(first, CUTOFF.minusDays(1), null).id();

        assertThatThrownBy(this::cleanupOutsideTransaction).isInstanceOf(java.time.DateTimeException.class);

        assertThat(ids()).containsExactly(id);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void cleanupDoesNotTouchOrWaitForValidTokenBeingCancelled() throws Exception {
        BusinessFixture business = business("UTC");
        LocalDateTime actualNow = LocalDateTime.now(ZoneOffset.UTC);
        TokenFixture valid = token(business, actualNow.plusDays(5), null);
        Long old = token(business, CUTOFF.minusDays(1), null).id();
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var cancellationResult = workers.submit(() -> tx().execute(status -> {
                cancellation.cancelPublicAppointment(valid.plain(), new PublicCancellationRequestDto());
                cancelled.countDown();
                awaitRelease(release);
                return true;
            }));
            assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
            var cleanupResult = workers.submit(this::cleanupOutsideTransaction);
            assertThat(cleanupResult.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(ids()).containsExactly(valid.id()).doesNotContain(old);
            release.countDown();
            assertThat(cancellationResult.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, business.appointmentId()))
                    .isEqualTo(AppointmentStatus.CANCELLED.name());
            assertThat(jdbc.queryForObject("SELECT used_at FROM appointment_public_tokens WHERE id = ?",
                    LocalDateTime.class, valid.id())).isAfterOrEqualTo(actualNow);
        } finally {
            release.countDown();
            stopWorkers(workers);
        }
    }

    @Test
    void cleanupWaitsForExpiredTokenCancellationLockThenDeletesAfterCancellationRollsBack() throws Exception {
        BusinessFixture business = business("UTC");
        TokenFixture expired = token(business, CUTOFF.minusDays(1), null);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var cancellationResult = workers.submit(() -> tx().execute(status -> {
                lock(expired);
                holderPid.set(backendPid());
                locked.countDown();
                awaitRelease(release);
                return cancellation.cancelPublicAppointment(expired.plain(), new PublicCancellationRequestDto());
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var cleanupResult = workers.submit(this::cleanupOutsideTransaction);
            awaitBlockedBy(holderPid.get());
            assertThat(cleanupResult.isDone()).isFalse();
            release.countDown();

            assertThatThrownBy(() -> cancellationResult.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(PublicTokenExpiredException.class);
            assertThat(cleanupResult.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(ids()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, business.appointmentId()))
                    .isEqualTo(AppointmentStatus.CONFIRMED.name());
        } finally {
            release.countDown();
            stopWorkers(workers);
        }
    }

    @Test
    void cancellationWaitsForCleanupDeleteThenSeesInvalidTokenAfterCommit() throws Exception {
        BusinessFixture business = business("UTC");
        TokenFixture expired = token(business, CUTOFF.minusDays(1), null);
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var cleanupResult = workers.submit(() -> tx().execute(status -> {
                holderPid.set(backendPid());
                int count = cleanup.cleanupPublicTokensBatch();
                deleted.countDown();
                awaitRelease(release);
                return count;
            }));
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
            var cancellationResult = workers.submit(() ->
                    cancellation.cancelPublicAppointment(expired.plain(), new PublicCancellationRequestDto()));
            awaitBlockedBy(holderPid.get());
            assertThat(cancellationResult.isDone()).isFalse();
            release.countDown();

            assertThat(cleanupResult.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThatThrownBy(() -> cancellationResult.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(InvalidPublicTokenException.class);
            assertThat(ids()).isEmpty();
        } finally {
            release.countDown();
            stopWorkers(workers);
        }
    }

    @Test
    void deleteRechecksCommittedConcurrentUseAndConsumesSelectedBudgetEvenWhenItDeletesNothing() throws Exception {
        properties.setBatchSize(1);
        BusinessFixture first = business("UTC");
        BusinessFixture next = business("UTC");
        TokenFixture changed = token(first, CUTOFF.minusDays(1), null);
        Long nextId = token(next, CUTOFF.minusDays(1), null).id();
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            // A controlled concurrent write isolates DELETE revalidation; an expired token
            // cannot actually be consumed by the public cancellation service.
            var updateResult = workers.submit(() -> tx().execute(status -> {
                AppointmentPublicToken token = lock(changed);
                token.setUsedAt(NOW);
                repository.saveAndFlush(token);
                holderPid.set(backendPid());
                updated.countDown();
                awaitRelease(release);
                return true;
            }));
            assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();
            var cleanupResult = workers.submit(this::cleanupOutsideTransaction);
            awaitBlockedBy(holderPid.get());
            release.countDown();

            assertThat(updateResult.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cleanupResult.get(10, TimeUnit.SECONDS)).isZero();
            assertThat(ids()).containsExactly(changed.id(), nextId);
            assertThat(cleanupOutsideTransaction()).isEqualTo(1);
            assertThat(ids()).containsExactly(changed.id());
        } finally {
            release.countDown();
            stopWorkers(workers);
        }
    }

    @Test
    void actualHibernateSelectionCanUseBothV4PartialIndexesWithSelectiveData() {
        BusinessFixture business = business("UTC");
        jdbc.update("""
                INSERT INTO appointment_public_tokens (appointment_id, token_hash, type, expires_at, used_at, created_at)
                SELECT ?, 'explain-' || g, 'CANCEL',
                       CASE WHEN g <= 10 THEN CAST(? AS timestamp) ELSE CAST(? AS timestamp) END,
                       CASE WHEN g % 2 = 0 THEN
                           CASE WHEN g <= 20 THEN CAST(? AS timestamp) ELSE CAST(? AS timestamp) END
                           ELSE NULL END,
                       CAST(? AS timestamp)
                FROM generate_series(1, 40000) g
                """, business.appointmentId(), CUTOFF.minusDays(1), NOW.plusDays(90), CUTOFF.minusDays(1), NOW, NOW);
        jdbc.execute("ANALYZE appointment_public_tokens");
        jdbc.execute("ANALYZE appointments");
        sqlCapture.start();
        String selectionSql;
        try {
            assertThat(repository.findCleanupCandidateIds(business.id(), CUTOFF, PageRequest.of(0, 500))).hasSize(15);
            selectionSql = sqlCapture.statements().stream()
                    .filter(sql -> sql.startsWith("select") && sql.contains("appointment_public_tokens"))
                    .findFirst().orElseThrow();
        } finally {
            sqlCapture.stop();
        }
        // Explain Hibernate's actual SQL with the same bindings. No planner options
        // are forced, and EXPLAIN without ANALYZE never executes a DELETE.
        String plan = jdbc.queryForObject("EXPLAIN (FORMAT JSON) " + selectionSql,
                String.class, business.id(), CUTOFF, CUTOFF, 500);
        assertThat(plan).contains("idx_public_tokens_cleanup_expires", "idx_public_tokens_cleanup_used");
        System.out.println("Public token cleanup selection EXPLAIN: " + plan);
    }

    private int cleanupOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        int deleted = cleanup.cleanupPublicTokensBatch();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return deleted;
    }

    private TransactionTemplate tx() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(25);
        return transaction;
    }

    private AppointmentPublicToken lock(TokenFixture token) {
        jdbc.execute("SET LOCAL lock_timeout = '20s'");
        return repository.findByTokenHashAndTypeForUpdate(hasher.hash(token.plain()), AppointmentPublicTokenType.CANCEL)
                .orElseThrow();
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitBlockedBy(int holderPid) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database()
                  AND wait_event_type = 'Lock'
                  AND ? = ANY(pg_blocking_pids(pid))
                  AND query LIKE '%appointment_public_tokens%'
                """, Integer.class, holderPid)).isPositive());
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release transaction");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Transaction worker interrupted", interrupted);
        }
    }

    private static void stopWorkers(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private BusinessFixture business(String timezone) {
        Long businessId = insert("""
                INSERT INTO businesses(name, slug, timezone, status, onboarding_status, created_at, updated_at)
                VALUES ('Cleanup test', ?, ?, 'ACTIVE', 'PENDING_SETUP', ?, ?) RETURNING id
                """, UUID.randomUUID().toString(), timezone, NOW, NOW);
        Long customerId = insert("""
                INSERT INTO customers(business_id, name, status, created_at, updated_at)
                VALUES (?, 'Test customer', 'ACTIVE', ?, ?) RETURNING id
                """, businessId, NOW, NOW);
        Long staffId = insert("""
                INSERT INTO staff_members(business_id, name, status, created_at, updated_at)
                VALUES (?, 'Test staff', 'ACTIVE', ?, ?) RETURNING id
                """, businessId, NOW, NOW);
        Long offeringId = insert("""
                INSERT INTO service_offerings(business_id, name, duration_minutes, price_cents, status, created_at, updated_at)
                VALUES (?, 'Test service', 60, 0, 'ACTIVE', ?, ?) RETURNING id
                """, businessId, NOW, NOW);
        LocalDateTime startsAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(5);
        Long appointmentId = insert("""
                INSERT INTO appointments(business_id, customer_id, staff_member_id, service_offering_id,
                    starts_at, ends_at, duration_minutes, price_cents, status, source, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 60, 0, 'CONFIRMED', 'PUBLIC_BOOKING', ?, ?) RETURNING id
                """, businessId, customerId, staffId, offeringId, startsAt, startsAt.plusHours(1), NOW, NOW);
        jdbc.update("""
                INSERT INTO booking_settings(business_id, public_booking_enabled, requires_customer_login,
                    booking_window_days, min_notice_hours, cancellation_notice_hours, slot_interval_minutes,
                    manual_confirmation_enabled, whatsapp_reminders_enabled, created_at, updated_at)
                VALUES (?, true, false, 30, 0, 0, 30, false, false, ?, ?)
                """, businessId, NOW, NOW);
        return new BusinessFixture(businessId, appointmentId);
    }

    private TokenFixture token(BusinessFixture business, LocalDateTime expiresAt, LocalDateTime usedAt) {
        String plain = UUID.randomUUID().toString();
        Long id = insert("""
                INSERT INTO appointment_public_tokens(appointment_id, token_hash, type, expires_at, used_at, created_at)
                VALUES (?, ?, 'CANCEL', ?, ?, ?) RETURNING id
                """, business.appointmentId(), hasher.hash(plain), expiresAt, usedAt, NOW.minusDays(90));
        return new TokenFixture(id, plain);
    }

    private Long insert(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private List<Long> ids() {
        return jdbc.queryForList("SELECT id FROM appointment_public_tokens ORDER BY id", Long.class);
    }

    private record BusinessFixture(Long id, Long appointmentId) { }
    private record TokenFixture(Long id, String plain) { }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> captured = new ThreadLocal<>();
        void start() { captured.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(captured.get()); }
        void stop() { captured.remove(); }
        @Override
        public String inspect(String sql) {
            List<String> statements = captured.get();
            if (statements != null) {
                statements.add(sql);
            }
            return sql;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = AppointmentPublicToken.class)
    @EnableJpaRepositories(basePackageClasses = AppointmentPublicTokenRepository.class)
    @EnableConfigurationProperties(PublicTokenCleanupProperties.class)
    static class CleanupTestConfiguration {
        @Bean
        Clock sessionCleanupClock() {
            // Deliberately different from all business zones; only its instant matters.
            return Clock.fixed(REFERENCE, ZoneId.of("Pacific/Honolulu"));
        }
        @Bean
        SqlCapture sqlCapture() { return new SqlCapture(); }
        @Bean
        HibernatePropertiesCustomizer statementInspector(SqlCapture capture) {
            return hibernateProperties -> hibernateProperties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
}

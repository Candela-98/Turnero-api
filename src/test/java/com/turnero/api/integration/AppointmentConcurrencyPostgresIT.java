package com.turnero.api.integration;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.PublicAppointmentCustomerRequestDto;
import com.turnero.api.dto.PublicAppointmentRequestDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.*;
import com.turnero.api.repository.*;
import com.turnero.api.security.PublicTokenHasher;
import com.turnero.api.service.AppointmentService;
import com.turnero.api.service.PublicBookingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
// No test-level transaction: fixtures and result reads are independent of the workers.
class AppointmentConcurrencyPostgresIT {

    private static final int DURATION_MINUTES = 30;
    private static final int PRICE_CENTS = 2000;
    // The coordinator owns the deadline; workers and SQL guards must outlive its observation.
    private static final Duration COORDINATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOCK_TIMEOUT = COORDINATION_TIMEOUT.plus(COMPLETION_TIMEOUT);
    private static final Duration STATEMENT_TIMEOUT = LOCK_TIMEOUT.plus(COMPLETION_TIMEOUT);
    private static final Duration CLEANUP_TIMEOUT = STATEMENT_TIMEOUT.plus(COMPLETION_TIMEOUT);
    private static final Duration WORKER_GUARD_TIMEOUT = COORDINATION_TIMEOUT.plus(CLEANUP_TIMEOUT);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Two worker connections plus an independent lock observer.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired AppointmentService admin;
    @Autowired PublicBookingService booking;
    @Autowired AppointmentRepository appointments;
    @Autowired BusinessRepository businesses;
    @Autowired StaffMemberRepository staff;
    @Autowired ServOfferingRepository services;
    @Autowired StaffServiceOfferingRepository assignments;
    @Autowired CustomerRepository customers;
    @Autowired BookingSettingsRepository settings;
    @Autowired BusinessHoursRepository businessHours;
    @Autowired StaffWorkingHoursRepository workingHours;
    @MockitoSpyBean AppointmentPublicTokenRepository tokens;
    @Autowired PublicTokenHasher tokenHasher;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager entityManager;
    @MockitoBean CurrentBusinessContext context;

    Long businessId;
    String businessSlug;
    Long staffMemberId;
    Long secondStaffMemberId;
    Long serviceOfferingId;
    Long firstCustomerId;
    Long secondCustomerId;
    String publicFirstEmail;
    String publicSecondEmail;
    LocalDateTime startsAt;
    AppointmentRequestDto firstRequest;
    AppointmentRequestDto secondRequest;

    @BeforeEach
    void setup() {
        String fixtureId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        startsAt = LocalDate.now(BUSINESS_ZONE).plusDays(3).atTime(13, 0);
        businessSlug = "concurrency-" + fixtureId;
        publicFirstEmail = "public-first-" + fixtureId + "@example.com";
        publicSecondEmail = "public-second-" + fixtureId + "@example.com";

        newTransaction().executeWithoutResult(status -> {
            businessId = businesses.saveAndFlush(Business.builder()
                    .name("Concurrency Studio").slug(businessSlug).timezone(BUSINESS_ZONE.getId())
                    .status(BusinessStatus.ACTIVE).onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                    .createdAt(now).updatedAt(now).build()).getId();
            staffMemberId = persistStaffMember("First professional", now);
            secondStaffMemberId = persistStaffMember("Second professional", now);
            serviceOfferingId = services.saveAndFlush(ServiceOffering.builder()
                    .businessId(businessId).name("30 minute service")
                    .durationMinutes(DURATION_MINUTES).priceCents(PRICE_CENTS)
                    .status(ServiceOfferingStatus.ACTIVE).createdAt(now).updatedAt(now).build()).getId();
            for (Long staffId : List.of(staffMemberId, secondStaffMemberId)) {
                assignments.saveAndFlush(StaffServiceOffering.builder()
                        .staffMemberId(staffId).serviceOfferingId(serviceOfferingId).build());
            }
            firstCustomerId = persistCustomer("First customer", "first-" + fixtureId + "@example.com",
                    "111111111", now);
            secondCustomerId = persistCustomer("Second customer", "second-" + fixtureId + "@example.com",
                    "222222222", now);
        });
        // executeWithoutResult has committed before requests can be used by other threads.
        when(context.getCurrentBusinessId()).thenReturn(businessId);
        firstRequest = adminRequest(firstCustomerId);
        secondRequest = adminRequest(secondCustomerId);
    }

    private Long persistStaffMember(String name, LocalDateTime now) {
        return staff.saveAndFlush(StaffMember.builder().businessId(businessId).name(name)
                .status(StaffMemberStatus.ACTIVE).createdAt(now).updatedAt(now).build()).getId();
    }

    private Long persistCustomer(String name, String email, String phone, LocalDateTime now) {
        return customers.saveAndFlush(Customer.builder().businessId(businessId).name(name)
                .email(email).phoneNumber(phone).status(CustomerStatus.ACTIVE)
                .createdAt(now).updatedAt(now).build()).getId();
    }

    AppointmentRequestDto adminRequest(Long customerId) {
        return adminRequest(customerId, staffMemberId, startsAt);
    }

    // Deliberately coherent snapshots: TURN-105/109 input invariants are not repaired by these tests.
    AppointmentRequestDto adminRequest(Long customerId, Long requestedStaffId, LocalDateTime requestedStart) {
        return AppointmentRequestDto.builder().customerId(customerId).staffMemberId(requestedStaffId)
                .serviceOfferingId(serviceOfferingId).startsAt(requestedStart)
                .endsAt(requestedStart.plusMinutes(DURATION_MINUTES)).durationMinutes(DURATION_MINUTES)
                .priceCents(PRICE_CENTS).status(AppointmentStatus.CONFIRMED).source(AppointmentSource.ADMIN).build();
    }

    private void preparePublicBooking(boolean manualConfirmation) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        newTransaction().executeWithoutResult(status -> {
            settings.saveAndFlush(BookingSettings.builder().businessId(businessId).publicBookingEnabled(true)
                    .requiresCustomerLogin(false).bookingWindowDays(30).minNoticeHours(0)
                    .cancellationNoticeHours(0).slotIntervalMinutes(15)
                    .manualConfirmationEnabled(manualConfirmation).createdAt(now).updatedAt(now).build());
            var day = com.turnero.api.model.enums.DayOfWeek.valueOf(startsAt.getDayOfWeek().name());
            businessHours.saveAndFlush(BusinessHours.builder().businessId(businessId).dayOfWeek(day)
                    .isClosed(false).opensAt(LocalTime.of(9, 0)).closesAt(LocalTime.of(18, 0)).build());
            workingHours.saveAndFlush(StaffWorkingHours.builder().staffMemberId(staffMemberId).dayOfWeek(day)
                    .isAvailable(true).startsAt(LocalTime.of(9, 0)).endsAt(LocalTime.of(18, 0)).build());
        });
    }

    private PublicAppointmentRequestDto publicRequest(String email, String phone, LocalDateTime start) {
        var customer = new PublicAppointmentCustomerRequestDto();
        customer.setName("Public visitor");
        customer.setEmail(email);
        customer.setPhoneNumber(phone);
        var request = new PublicAppointmentRequestDto();
        request.setServiceOfferingId(serviceOfferingId);
        request.setStaffMemberId(staffMemberId.toString());
        request.setStartsAt(start);
        request.setCustomer(customer);
        return request;
    }

    private Booking reserveAdmin(AppointmentRequestDto request) {
        return new Booking(admin.saveAppointment(request).getId(), null);
    }

    private Booking reservePublic(PublicAppointmentRequestDto request) {
        var response = booking.createPublicAppointment(businessSlug, request);
        return new Booking(response.getAppointmentId(), response.getCancelToken());
    }

    @Test
    void adminBooking_directServiceCall_ownsTransactionAndCommits() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        // Observes the service entry, before any repository can create its own transaction.
        // Removing @Transactional from saveAppointment makes these assertions fail.
        when(context.getCurrentBusinessId()).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("saveAppointment must start its own transaction").isTrue();
            assertThat(entityManager.isJoinedToTransaction()).isTrue();
            return businessId;
        });

        var response = admin.saveAppointment(firstRequest);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertSingleAppointment(new Booking(response.getId(), null), firstCustomerId,
                AppointmentSource.ADMIN, startsAt, AppointmentStatus.CONFIRMED);
        assertThatThrownBy(() -> admin.saveAppointment(secondRequest))
                .isExactlyInstanceOf(AppointmentOverlapException.class);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(persistedAppointments()).hasSize(1);
        assertThat(tokenCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"PENDING", "CONFIRMED"})
    void concurrentAdminBookings_sameStaffAndStart_onlyOneCommits(AppointmentStatus status) throws Exception {
        firstRequest.setStatus(status);
        secondRequest.setStatus(status);
        Race result = contend(() -> reserveAdmin(firstRequest), () -> reserveAdmin(secondRequest), false, true);
        assertSingleAppointment(result.first(), firstCustomerId, AppointmentSource.ADMIN, startsAt, status);
        assertThat(tokenCount()).isZero();
    }

    @Test
    void concurrentAdminBookings_sameStaffAndPartialOverlap_onlyOneCommits() throws Exception {
        secondRequest = adminRequest(secondCustomerId, staffMemberId, startsAt.plusMinutes(15));
        Race result = contend(() -> reserveAdmin(firstRequest), () -> reserveAdmin(secondRequest), false, true);
        assertSingleAppointment(result.first(), firstCustomerId, AppointmentSource.ADMIN, startsAt, AppointmentStatus.CONFIRMED);
        assertThat(tokenCount()).isZero();
    }

    @Test
    void concurrentAdminBookings_sameStaffAndContiguousSlots_bothCommit() throws Exception {
        secondRequest = adminRequest(secondCustomerId, staffMemberId, startsAt.plusMinutes(DURATION_MINUTES));
        Race result = contend(() -> reserveAdmin(firstRequest), () -> reserveAdmin(secondRequest), false, false);
        assertAdminPair(result.first(), firstRequest, result.second(), secondRequest);
        assertThat(tokenCount()).isZero();
    }

    @Test
    void concurrentAdminBookings_differentStaffAndSameStart_bothCommit() throws Exception {
        secondRequest = adminRequest(secondCustomerId, secondStaffMemberId, startsAt);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CompletableFuture<Booking> prepared = new CompletableFuture<>();
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Throwable primaryFailure = null;
        long coordinationDeadline = deadlineAfter(COORDINATION_TIMEOUT);
        try {
            Future<Booking> first = executor.submit(() -> heldBooking(
                    () -> reserveAdmin(firstRequest), firstPid, prepared, releaseFirst, false));
            Booking firstBooking = getBefore(prepared, coordinationDeadline);
            Future<Booking> second = executor.submit(() -> independentBooking(
                    () -> reserveAdmin(secondRequest), secondPid));
            assertThat(getBefore(secondPid, coordinationDeadline))
                    .isNotEqualTo(getBefore(firstPid, coordinationDeadline));
            await().alias("second professional booking completes while the first remains uncommitted")
                    .atMost(remaining(coordinationDeadline))
                    .until(() -> {
                        requirePending(first, "first worker");
                        return second.isDone();
                    });
            Booking secondBooking = getBefore(second, coordinationDeadline);
            // A committed result for B while A still holds its lock proves independent progress.
            requirePending(first, "first worker");
            assertThat(appointments.findById(firstBooking.id())).isEmpty();
            assertThat(appointments.findById(secondBooking.id())).isPresent();
            long completionDeadline = deadlineAfter(COMPLETION_TIMEOUT);
            releaseFirst.countDown();
            assertThat(getBefore(first, completionDeadline)).isEqualTo(firstBooking);
            assertAdminPair(firstBooking, firstRequest, secondBooking, secondRequest);
            assertThat(tokenCount()).isZero();
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            releaseFirst.countDown();
            shutdown(executor, primaryFailure);
        }
    }

    @Test
    void concurrentAdminBookings_firstRollsBack_secondCommits() throws Exception {
        Race result = contend(() -> reserveAdmin(firstRequest), () -> reserveAdmin(secondRequest), true, false);
        assertThat(appointments.findById(result.first().id())).isEmpty();
        assertSingleAppointment(result.second(), secondCustomerId, AppointmentSource.ADMIN, startsAt,
                AppointmentStatus.CONFIRMED);
        assertThat(tokenCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentPublicBookings_sameStaffAndStart_onlyWinnerAndTokenPersist(boolean manualConfirmation) throws Exception {
        preparePublicBooking(manualConfirmation);
        var first = publicRequest(publicFirstEmail, "333333333", startsAt);
        var second = publicRequest(publicSecondEmail, "444444444", startsAt);
        Race result = contend(() -> reservePublic(first), () -> reservePublic(second), false, true);
        assertPublicWinner(result.first(), manualConfirmation ? AppointmentStatus.PENDING : AppointmentStatus.CONFIRMED);
        assertThat(customerCount(publicSecondEmail)).isZero();
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(3);
    }

    @Test
    void concurrentPublicBookings_partialOverlap_onlyWinnerAndTokenPersist() throws Exception {
        preparePublicBooking(false);
        var first = publicRequest(publicFirstEmail, "333333333", startsAt);
        var second = publicRequest(publicSecondEmail, "444444444", startsAt.plusMinutes(15));
        Race result = contend(() -> reservePublic(first), () -> reservePublic(second), false, true);
        assertPublicWinner(result.first(), AppointmentStatus.CONFIRMED);
        assertThat(customerCount(publicSecondEmail)).isZero();
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(3);
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,15", "true,0", "true,15"})
    void concurrentMixedBookings_shareStaffLockAndOnlyFirstCommits(boolean publicFirst, int offsetMinutes) throws Exception {
        preparePublicBooking(false);
        AppointmentRequestDto adminRequest = adminRequest(firstCustomerId, staffMemberId,
                publicFirst ? startsAt.plusMinutes(offsetMinutes) : startsAt);
        var publicRequest = publicRequest(publicFirstEmail, "333333333",
                publicFirst ? startsAt : startsAt.plusMinutes(offsetMinutes));
        Supplier<Booking> first = publicFirst ? () -> reservePublic(publicRequest) : () -> reserveAdmin(adminRequest);
        Supplier<Booking> second = publicFirst ? () -> reserveAdmin(adminRequest) : () -> reservePublic(publicRequest);
        Race result = contend(first, second, false, true);
        if (publicFirst) {
            assertPublicWinner(result.first(), AppointmentStatus.CONFIRMED);
            assertThat(customers.findAllByBusinessId(businessId)).hasSize(3);
        } else {
            assertSingleAppointment(result.first(), firstCustomerId, AppointmentSource.ADMIN, startsAt,
                    AppointmentStatus.CONFIRMED);
            assertThat(tokenCount()).isZero();
            assertThat(customerCount(publicFirstEmail)).isZero();
            assertThat(customers.findAllByBusinessId(businessId)).hasSize(2);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicBooking_tokenPersistenceFailure_rollsBackAppointmentCustomerAndToken(boolean restoreArchived) {
        preparePublicBooking(false);
        String email = publicFirstEmail;
        String phone = "333333333";
        LocalDateTime originalUpdatedAt = LocalDateTime.now(BUSINESS_ZONE).minusDays(1).withNano(0);
        if (restoreArchived) {
            Customer archived = customers.findById(firstCustomerId).orElseThrow();
            email = archived.getEmail();
            phone = archived.getPhoneNumber();
            newTransaction().executeWithoutResult(status -> {
                Customer customer = customers.findById(firstCustomerId).orElseThrow();
                customer.setStatus(CustomerStatus.INACTIVE);
                customer.setUpdatedAt(originalUpdatedAt);
                customers.saveAndFlush(customer);
            });
        }
        var request = publicRequest(email, phone, startsAt);
        AtomicLong attemptedAppointmentId = new AtomicLong();
        AtomicLong attemptedTokenId = new AtomicLong();
        AtomicLong attemptedCustomerId = new AtomicLong();

        // Only this failure test intercepts save, delegating to the real saveAndFlush before injecting failure.
        doAnswer(invocation -> {
            AppointmentPublicToken saved = tokens.saveAndFlush(invocation.getArgument(0));
            entityManager.flush();
            attemptedAppointmentId.set(saved.getAppointmentId());
            attemptedTokenId.set(saved.getId());
            Appointment appointment = appointments.findById(saved.getAppointmentId()).orElseThrow();
            attemptedCustomerId.set(appointment.getCustomerId());
            assertThat(customers.findById(appointment.getCustomerId()).orElseThrow().getStatus())
                    .isEqualTo(CustomerStatus.ACTIVE);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_public_tokens WHERE id = ?",
                    Long.class, saved.getId())).isEqualTo(1L);
            // A real duplicate-hash INSERT fails in PostgreSQL, after all three entities reached the DB.
            jdbc.update("""
                    INSERT INTO appointment_public_tokens
                        (appointment_id, token_hash, type, expires_at, used_at, created_at)
                    SELECT appointment_id, token_hash, type, expires_at, used_at, created_at
                    FROM appointment_public_tokens WHERE id = ?
                    """, saved.getId());
            throw new AssertionError("The duplicate token hash must be rejected by PostgreSQL");
        }).when(tokens).save(any(AppointmentPublicToken.class));

        assertThatThrownBy(() -> booking.createPublicAppointment(businessSlug, request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .satisfies(cause -> assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("23505"));
        assertThat(attemptedAppointmentId.get()).isPositive();
        assertThat(attemptedTokenId.get()).isPositive();
        assertThat(persistedAppointments()).isEmpty();
        assertThat(appointments.findById(attemptedAppointmentId.get())).isEmpty();
        assertThat(tokens.findById(attemptedTokenId.get())).isEmpty();
        assertThat(tokenCount()).isZero();
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(2);
        if (restoreArchived) {
            Customer restored = customers.findById(firstCustomerId).orElseThrow();
            assertThat(attemptedCustomerId.get()).isEqualTo(firstCustomerId);
            assertThat(restored.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
            assertThat(restored.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        } else {
            assertThat(customers.findById(attemptedCustomerId.get())).isEmpty();
            assertThat(customerCount(publicFirstEmail)).isZero();
        }
    }

    private Race contend(Supplier<Booking> firstOperation, Supplier<Booking> secondOperation,
                         boolean rollbackFirst, boolean secondConflicts) throws Exception {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CompletableFuture<Booking> prepared = new CompletableFuture<>();
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Throwable primaryFailure = null;
        // Preparation, PID publication and lock observation share this single deadline.
        long coordinationDeadline = deadlineAfter(COORDINATION_TIMEOUT);
        try {
            Future<Booking> first = executor.submit(() -> heldBooking(
                    firstOperation, firstPid, prepared, releaseFirst, rollbackFirst));
            Booking attemptedFirst = getBefore(prepared, coordinationDeadline);
            Future<Booking> second = executor.submit(() -> independentBooking(secondOperation, secondPid));
            int holder = getBefore(firstPid, coordinationDeadline);
            int waiter = getBefore(secondPid, coordinationDeadline);
            assertThat(waiter).isNotEqualTo(holder);
            await().alias("second PostgreSQL backend waiting for the first professional lock")
                    .atMost(remaining(coordinationDeadline))
                    .until(() -> {
                        requirePending(first, "first worker");
                        requirePending(second, "second worker");
                        return isWaitingForStaffLock(waiter, holder);
                    });
            requirePending(first, "first worker");
            requirePending(second, "second worker");
            // Observer uses a third connection: the first INSERT is not committed yet.
            assertThat(appointments.findById(attemptedFirst.id())).isEmpty();
            // Both transaction outcomes share a second, bounded completion deadline.
            long completionDeadline = deadlineAfter(COMPLETION_TIMEOUT);
            releaseFirst.countDown();
            Booking firstResult = getBefore(first, completionDeadline);
            Booking secondResult = null;
            if (secondConflicts) {
                assertThatThrownBy(() -> getBefore(second, completionDeadline))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseExactlyInstanceOf(AppointmentOverlapException.class);
            } else {
                secondResult = getBefore(second, completionDeadline);
            }
            return new Race(firstResult, secondResult);
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            releaseFirst.countDown();
            shutdown(executor, primaryFailure);
        }
    }

    private Booking heldBooking(Supplier<Booking> operation, CompletableFuture<Integer> pid,
                                CompletableFuture<Booking> prepared, CountDownLatch release, boolean rollback) {
        try {
            return newTransaction().execute(status -> {
                configureWorker();
                pid.complete(currentBackendPid());
                Booking result = operation.get();
                entityManager.flush();
                prepared.complete(result);
                awaitRelease(release);
                if (rollback) status.setRollbackOnly();
                return result;
            });
        } catch (RuntimeException | Error failure) {
            pid.completeExceptionally(failure);
            prepared.completeExceptionally(failure);
            throw failure;
        }
    }

    private Booking independentBooking(Supplier<Booking> operation, CompletableFuture<Integer> pid) {
        try {
            return newTransaction().execute(status -> {
                configureWorker();
                pid.complete(currentBackendPid());
                return operation.get();
            });
        } catch (RuntimeException | Error failure) {
            pid.completeExceptionally(failure);
            throw failure;
        }
    }

    private TransactionTemplate newTransaction() {
        var template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void configureWorker() {
        // Assert the effective environment instead of silently changing its isolation level.
        assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
        jdbc.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT.toMillis() + "ms'");
        jdbc.execute("SET LOCAL statement_timeout = '" + STATEMENT_TIMEOUT.toMillis() + "ms'");
    }

    private static void awaitRelease(CountDownLatch latch) {
        try {
            if (!latch.await(WORKER_GUARD_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new AssertionError("Timed out waiting for permission to finish the first transaction");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", interrupted);
        }
    }

    private static long deadlineAfter(Duration timeout) {
        return System.nanoTime() + timeout.toNanos();
    }

    private static Duration remaining(long deadline) throws TimeoutException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new TimeoutException("The shared test phase deadline expired");
        return Duration.ofNanos(nanos);
    }

    private static <T> T getBefore(Future<T> future, long deadline) throws Exception {
        return future.get(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void requirePending(Future<?> future, String worker) throws Exception {
        if (future.isDone()) {
            // get preserves the real worker exception instead of reporting a lock-observation timeout.
            future.get(0, TimeUnit.NANOSECONDS);
            throw new AssertionError(worker + " completed before the coordinator released the first transaction");
        }
    }

    private static void shutdown(ExecutorService executor, Throwable primaryFailure) throws Exception {
        // Clear an existing interrupt only during bounded cleanup, and restore it afterwards.
        boolean interrupted = Thread.interrupted() || primaryFailure instanceof InterruptedException;
        Throwable cleanupFailure = null;
        long deadline = deadlineAfter(CLEANUP_TIMEOUT);
        try {
            executor.shutdownNow();
            while (!executor.isTerminated()) {
                try {
                    assertThat(executor.awaitTermination(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS))
                            .as("workers must terminate and release their transactions").isTrue();
                } catch (InterruptedException failure) {
                    interrupted = true;
                    if (cleanupFailure == null) cleanupFailure = failure;
                    else cleanupFailure.addSuppressed(failure);
                    // Still wait for worker termination, bounded by the original cleanup deadline.
                }
            }
        } catch (Exception | Error failure) {
            if (cleanupFailure == null) cleanupFailure = failure;
            else cleanupFailure.addSuppressed(failure);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (cleanupFailure != null) {
            if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
            else if (cleanupFailure instanceof Exception failure) throw failure;
            else throw (Error) cleanupFailure;
        }
    }

    int currentBackendPid() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Query the backend PID inside the worker transaction");
        }
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    boolean isWaitingForStaffLock(int waitingPid, int blockingPid) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Observe locks outside worker transactions, using a separate connection");
        }
        if (waitingPid <= 0 || blockingPid <= 0 || waitingPid == blockingPid) {
            throw new IllegalArgumentException("Lock observation requires two distinct, positive backend PIDs");
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity activity
                    WHERE activity.pid = ?
                      AND activity.datname = current_database()
                      AND activity.wait_event_type = 'Lock'
                      AND ? = ANY (pg_blocking_pids(activity.pid))
                      AND activity.query ILIKE '%staff_members%'
                )
                """, Boolean.class, waitingPid, blockingPid));
    }

    private List<Appointment> persistedAppointments() {
        return appointments.findAllByBusinessId(businessId);
    }

    private long customerCount(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM customers WHERE business_id = ? AND email = ?",
                Long.class, businessId, email);
    }

    private long tokenCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM appointment_public_tokens token
                JOIN appointments appointment ON appointment.id = token.appointment_id
                WHERE appointment.business_id = ?
                """, Long.class, businessId);
    }

    private void assertAdminPair(Booking first, AppointmentRequestDto firstExpected,
                                 Booking second, AppointmentRequestDto secondExpected) {
        assertThat(first.id()).isNotNull().isNotEqualTo(second.id());
        assertThat(second.id()).isNotNull();
        List<Appointment> saved = persistedAppointments();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Appointment::getId).containsExactlyInAnyOrder(first.id(), second.id());
        assertAdminAppointment(appointments.findById(first.id()).orElseThrow(), first, firstExpected);
        assertAdminAppointment(appointments.findById(second.id()).orElseThrow(), second, secondExpected);
    }

    private void assertAdminAppointment(Appointment saved, Booking result, AppointmentRequestDto expected) {
        assertThat(saved.getId()).isEqualTo(result.id());
        assertThat(saved.getBusinessId()).isEqualTo(businessId);
        assertThat(saved.getStaffMemberId()).isEqualTo(expected.getStaffMemberId());
        assertThat(saved.getCustomerId()).isEqualTo(expected.getCustomerId());
        assertThat(saved.getServiceOfferingId()).isEqualTo(expected.getServiceOfferingId());
        assertThat(saved.getStartsAt()).isEqualTo(expected.getStartsAt());
        assertThat(saved.getEndsAt()).isEqualTo(expected.getEndsAt());
        assertThat(saved.getDurationMinutes()).isEqualTo(expected.getDurationMinutes());
        assertThat(saved.getPriceCents()).isEqualTo(expected.getPriceCents());
        assertThat(saved.getStatus()).isEqualTo(expected.getStatus());
        assertThat(saved.getSource()).isEqualTo(AppointmentSource.ADMIN);
    }

    private void assertSingleAppointment(Booking winner, Long customerId, AppointmentSource source,
                                         LocalDateTime start, AppointmentStatus status) {
        assertThat(persistedAppointments()).singleElement().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(winner.id());
            assertThat(saved.getCustomerId()).isEqualTo(customerId);
            assertThat(saved.getStaffMemberId()).isEqualTo(staffMemberId);
            assertThat(saved.getServiceOfferingId()).isEqualTo(serviceOfferingId);
            assertThat(saved.getSource()).isEqualTo(source);
            assertThat(saved.getStatus()).isEqualTo(status);
            assertThat(saved.getStartsAt()).isEqualTo(start);
            assertThat(saved.getEndsAt()).isEqualTo(start.plusMinutes(DURATION_MINUTES));
            assertThat(saved.getDurationMinutes()).isEqualTo(DURATION_MINUTES);
        });
    }

    private void assertPublicWinner(Booking winner, AppointmentStatus status) {
        assertThat(winner.cancelToken()).isNotBlank();
        Customer customer = customers.findByBusinessIdAndEmailIgnoreCase(businessId, publicFirstEmail).orElseThrow();
        assertSingleAppointment(winner, customer.getId(), AppointmentSource.PUBLIC_BOOKING, startsAt, status);
        assertThat(tokenCount()).isEqualTo(1);
        AppointmentPublicToken token = tokens.findByTokenHashAndType(
                tokenHasher.hash(winner.cancelToken()), AppointmentPublicTokenType.CANCEL).orElseThrow();
        assertThat(token.getAppointmentId()).isEqualTo(winner.id());
        assertThat(token.getExpiresAt()).isEqualTo(startsAt);
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.getTokenHash()).isNotEqualTo(winner.cancelToken());
    }

    private record Booking(Long id, String cancelToken) {}
    private record Race(Booking first, Booking second) {}
}

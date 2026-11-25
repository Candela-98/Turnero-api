package com.turnero.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.*;
import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.enums.SessionScope;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import com.turnero.api.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerAppointmentBookingPostgresIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerAuthService auth;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired CustomerAppointmentBookingService authenticated;
    @Autowired SessionService sessions;
    @Autowired SessionProperties properties;
    @Autowired PublicBookingService guestBooking;
    @MockitoBean GoogleIdentityService google;
    @MockitoSpyBean AppointmentPublicTokenRepository tokens;
    String slug, otherSlug, email;
    long businessId, otherBusinessId;
    PublicAppointmentRequestDto guestRequest;
    CustomerAppointmentRequestDto request;
    Cookie alice;

    @BeforeEach
    void setup() {
        String fixture = UUID.randomUUID().toString();
        slug = "booking-" + fixture;
        otherSlug = "other-" + fixture;
        businessId = business(slug);
        otherBusinessId = business(otherSlug);
        email = fixture + "@example.test";
        when(google.verify(anyString())).thenAnswer(call -> new GoogleIdentityDto(
                fixture + call.getArgument(0), call.getArgument(0).equals("alice") ? email : fixture + "bob@example.test", true));
        guestRequest = bookingFixture(businessId);
        request = new CustomerAppointmentRequestDto(guestRequest.getServiceOfferingId(),
                guestRequest.getStaffMemberId(), guestRequest.getStartsAt(), "Customer note");
        alice = login(slug, "alice");
    }

    private Cookie login(String businessSlug, String identity) {
        return new Cookie(properties.getCustomerCookieName(), auth.login(businessSlug, identity, null, null).token());
    }

    private String path(String businessSlug) {
        return "/api/v1/customer/businesses/" + businessSlug + "/appointments";
    }

    private long customerId(Cookie cookie) {
        return sessions.validateSession(cookie.getValue(), SessionScope.CUSTOMER).getCustomerId();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE business_id = ?", Long.class, businessId);
    }

    private long tokenCount() {
        return jdbc.queryForObject("SELECT count(*) FROM appointment_public_tokens t JOIN appointments a ON a.id=t.appointment_id WHERE a.business_id=?", Long.class, businessId);
    }

    private void noReservation() {
        assertThat(count("appointments")).isZero();
        assertThat(tokenCount()).isZero();
        assertThat(count("customers")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsOwnedAppointmentAndListsItWithoutClaimingGuests(boolean manual) throws Exception {
        jdbc.update("UPDATE booking_settings SET manual_confirmation_enabled=? WHERE business_id=?", manual, businessId);
        // A guest created before Google login remains unowned even though the profile is linked later.
        String historicalEmail = UUID.randomUUID() + "@example.test";
        guestRequest.getCustomer().setEmail(historicalEmail);
        guestRequest.getCustomer().setPhoneNumber("987654321");
        var guest = guestBooking.createPublicAppointment(slug, guestRequest);
        when(google.verify("historical")).thenReturn(new GoogleIdentityDto(historicalEmail, historicalEmail, true));
        Cookie linked = login(slug, "historical");
        var later = new CustomerAppointmentRequestDto(request.serviceOfferingId(), "any", request.startsAt().plusHours(1), null);
        var result = mvc.perform(post(path(slug)).cookie(linked).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(later)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(manual ? "PENDING" : "CONFIRMED"))
                .andExpect(jsonPath("$.duration_minutes").value(30)).andExpect(jsonPath("$.price_cents").value(1000))
                .andExpect(jsonPath("$.cancel_token").isNotEmpty()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("appointment_id").asLong();
        long user = sessions.validateSession(linked.getValue(), SessionScope.CUSTOMER).getUserId();
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id=?", Long.class, id)).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM appointments WHERE id=?", Long.class, id)).isEqualTo(customerId(linked));
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id=?", Long.class, guest.getAppointmentId())).isNull();
        guestRequest.setStartsAt(request.startsAt().plusHours(2));
        var guestResponse = mvc.perform(post("/api/v1/public/businesses/" + slug + "/appointments").cookie(linked)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(guestRequest)))
                .andExpect(status().isCreated()).andReturn();
        long newGuest = json.readTree(guestResponse.getResponse().getContentAsString()).get("appointment_id").asLong();
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id=?", Long.class, newGuest)).isNull();
        mvc.perform(get(path(slug)).cookie(linked)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].starts_at").value(later.startsAt() + ":00Z"));
        mvc.perform(get(path(slug)).cookie(alice)).andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner_user_id", "ownerUserId", "user_id", "userId", "customer_id", "customerId", "business_id", "customer", "owner", "status", "source", "price_cents", "ends_at"})
    void bodyCannotSelectIdentityOrBackendFields(String field) throws Exception {
        var body = json.valueToTree(request);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put(field, 123);
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        noReservation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"service_offering_id\":4}",
            "{\"service_offering_id\":4,\"staff_member_id\":\" \",\"starts_at\":\"2099-01-01T12:00:00\"}",
            "{\"service_offering_id\":4,\"staff_member_id\":\"8\",\"starts_at\":\"2000-01-01T12:00:00\"}",
            "{\"service_offering_id\":4,\"staff_member_id\":\"8\",\"starts_at\":\"bad-date\"}"})
    void malformedOrIncompleteBodiesDoNotWrite(String body) throws Exception {
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        noReservation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "invalid", "expired", "revoked", "wrong-scope", "admin-cookie"})
    void rejectsInvalidSessions(String kind) throws Exception {
        var call = post(path(slug)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request));
        var session = sessions.validateSession(alice.getValue(), SessionScope.CUSTOMER);
        switch (kind) {
            case "invalid" -> call.cookie(new Cookie(alice.getName(), "invalid"));
            case "expired" -> {
                jdbc.update("UPDATE user_sessions SET expires_at=NOW()-INTERVAL '1 day' WHERE id=?", session.getId());
                call.cookie(alice);
            }
            case "revoked" -> {
                sessions.revokeSession(alice.getValue(), SessionScope.CUSTOMER);
                call.cookie(alice);
            }
            case "wrong-scope", "admin-cookie" -> call.cookie(new Cookie(
                    kind.equals("admin-cookie") ? properties.getCookieName() : alice.getName(),
                    sessions.createSession(session.getUserId(), SessionScope.ADMIN, null, null)));
            default -> { }
        }
        mvc.perform(call).andExpect(status().isUnauthorized());
        noReservation();
    }

    @Test
    void isolatesAccountsBusinessesAndReferences() throws Exception {
        Cookie bob = login(slug, "bob");
        Cookie aliceOther = login(otherSlug, "alice");
        var otherRequest = bookingFixture(otherBusinessId);
        mvc.perform(post(path(slug)).cookie(aliceOther).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path(otherSlug)).cookie(aliceOther).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mvc.perform(get(path(slug)).cookie(bob)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get(path(otherSlug)).cookie(aliceOther)).andExpect(jsonPath("$.data").isEmpty());
        var foreignStaff = new CustomerAppointmentRequestDto(otherRequest.getServiceOfferingId(),
                request.staffMemberId(), request.startsAt(), null);
        mvc.perform(post(path(otherSlug)).cookie(aliceOther).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(foreignStaff)))
                .andExpect(status().isNotFound());
        var ownOther = new CustomerAppointmentRequestDto(otherRequest.getServiceOfferingId(),
                otherRequest.getStaffMemberId(), otherRequest.getStartsAt(), null);
        mvc.perform(post(path(otherSlug)).cookie(aliceOther).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ownOther)))
                .andExpect(status().isCreated());
        var bobRequest = new CustomerAppointmentRequestDto(request.serviceOfferingId(), request.staffMemberId(), request.startsAt().plusHours(1), null);
        mvc.perform(post(path(slug)).cookie(bob).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bobRequest)))
                .andExpect(status().isCreated());
        for (Cookie cookie : List.of(alice, bob)) {
            mvc.perform(get(path(slug)).cookie(cookie)).andExpect(jsonPath("$.data.length()").value(1));
        }
        mvc.perform(get(path(otherSlug)).cookie(aliceOther)).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post(path("missing")).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksWithoutRestoringOrChangingLoginData() throws Exception {
        long customer = customerId(alice);
        jdbc.update("UPDATE customers SET status='INACTIVE',public_booking_blocked=true,public_booking_block_reason='Private' WHERE id=?", customer);
        var before = jdbc.queryForMap("SELECT * FROM customers WHERE id=?", customer);
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CUSTOMER_BOOKING_BLOCKED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private"))));
        assertThat(jdbc.queryForMap("SELECT * FROM customers WHERE id=?", customer)).isEqualTo(before);
        noReservation();
    }

    @Test
    void archivedUnblockedProfileIsRestoredWithoutContactResolution() throws Exception {
        long customer = customerId(alice);
        jdbc.update("UPDATE customers SET status='INACTIVE',email=NULL,phone_number=NULL WHERE id=?", customer);
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated());
        assertThat(count("customers")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM customers WHERE id=?", String.class, customer)).isEqualTo("ACTIVE");
    }

    @Test
    void stableSessionProfileWinsOverAmbiguousContactsAndAnotherProfilesBlock() throws Exception {
        Cookie bob = login(slug, "bob");
        jdbc.update("UPDATE customers SET email=?,public_booking_blocked=true,public_booking_block_reason='Private' WHERE id=?",
                email, customerId(bob));
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated());
        assertThat(count("customers")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM appointments WHERE business_id=?", Long.class, businessId))
                .isEqualTo(customerId(alice));
    }

    @Test
    void changedProfileBusinessIsRejectedWithoutFallback() throws Exception {
        jdbc.update("UPDATE customers SET business_id=? WHERE id=?", otherBusinessId, customerId(alice));
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        assertThat(count("customers")).isZero();
        assertThat(count("appointments")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE business_id=?", Long.class, otherBusinessId)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"slot", "business", "disabled", "notice", "window", "staff", "service"})
    void bookingFailuresDoNotWrite(String kind) throws Exception {
        int status = switch (kind) {
            case "slot" -> { guestBooking.createPublicAppointment(slug, guestRequest); yield 409; }
            case "business" -> { jdbc.update("UPDATE businesses SET status='INACTIVE' WHERE id=?", businessId); yield 403; }
            case "disabled" -> { jdbc.update("UPDATE booking_settings SET public_booking_enabled=false WHERE business_id=?", businessId); yield 403; }
            case "notice" -> { jdbc.update("UPDATE booking_settings SET min_notice_hours=1000 WHERE business_id=?", businessId); yield 400; }
            case "window" -> { jdbc.update("UPDATE booking_settings SET booking_window_days=1 WHERE business_id=?", businessId); yield 400; }
            case "staff" -> { jdbc.update("UPDATE staff_members SET status='INACTIVE' WHERE id=?", Long.valueOf(request.staffMemberId())); yield 403; }
            default -> { jdbc.update("UPDATE service_offerings SET status='INACTIVE' WHERE id=?", request.serviceOfferingId()); yield 403; }
        };
        jdbc.update("UPDATE customers SET status='INACTIVE' WHERE id=?", customerId(alice));
        var before = jdbc.queryForMap("SELECT * FROM customers WHERE id=?", customerId(alice));
        long count = count("appointments"), tokens = tokenCount();
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().is(status));
        assertThat(count("appointments")).isEqualTo(count);
        assertThat(tokenCount()).isEqualTo(tokens);
        assertThat(jdbc.queryForMap("SELECT * FROM customers WHERE id=?", customerId(alice))).isEqualTo(before);
    }

    @Test
    void bookingLogsDoNotExposeCookiesTokensNotesOrUnexpectedExceptionMessages() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String note = "private-note-" + UUID.randomUUID(), failure = "private-error-" + UUID.randomUUID();
        try {
            var body = new CustomerAppointmentRequestDto(request.serviceOfferingId(), request.staffMemberId(), request.startsAt(), note);
            var result = mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isCreated()).andReturn();
            String cancellationToken = json.readTree(result.getResponse().getContentAsString()).get("cancel_token").asText();
            var next = new CustomerAppointmentRequestDto(request.serviceOfferingId(), request.staffMemberId(), request.startsAt().plusHours(1), note);
            doThrow(new IllegalStateException(failure)).when(tokens).save(any(AppointmentPublicToken.class));
            mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(next)))
                    .andExpect(status().isInternalServerError());
            var invalid = json.valueToTree(body);
            ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("owner_user_id", failure);
            mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
                    .andExpect(status().isBadRequest());
            String logs = appender.list.stream().map(event -> event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? "" : ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy())))
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("Unexpected error occurred (IllegalStateException)")
                    .doesNotContain(note, failure, email, alice.getValue(), cancellationToken);
            assertThat(count("appointments")).isEqualTo(1);
            assertThat(tokenCount()).isEqualTo(1);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void laterDatabaseFailureRollsBackReservationAndRestorationButKeepsLogin() throws Exception {
        long customer = customerId(alice);
        jdbc.update("UPDATE customers SET status='INACTIVE' WHERE id=?", customer);
        var before = jdbc.queryForMap("SELECT * FROM customers WHERE id=?", customer);
        var session = sessions.validateSession(alice.getValue(), SessionScope.CUSTOMER);
        var loginUser = jdbc.queryForMap("SELECT * FROM users WHERE id=?", session.getUserId());
        var loginSession = jdbc.queryForMap("SELECT * FROM user_sessions WHERE id=?", session.getId());
        doAnswer(call -> {
            AppointmentPublicToken token = (AppointmentPublicToken) call.callRealMethod();
            assertThat(count("appointments")).isEqualTo(1);
            assertThat(tokenCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM customers WHERE id=?", String.class, customer)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id=?", Long.class, token.getAppointmentId())).isEqualTo(session.getUserId());
            jdbc.update("INSERT INTO appointment_public_tokens (appointment_id,token_hash,type,expires_at,created_at) SELECT appointment_id,token_hash,type,expires_at,created_at FROM appointment_public_tokens WHERE id=?", token.getId());
            throw new AssertionError("Expected unique constraint violation");
        }).when(tokens).save(any(AppointmentPublicToken.class));
        mvc.perform(post(path(slug)).cookie(alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
        verify(tokens).save(any(AppointmentPublicToken.class));
        noReservation();
        assertThat(jdbc.queryForMap("SELECT * FROM customers WHERE id=?", customer)).isEqualTo(before);
        assertThat(sessions.validateSession(alice.getValue(), SessionScope.CUSTOMER).getUserId()).isEqualTo(session.getUserId());
        assertThat(jdbc.queryForMap("SELECT * FROM users WHERE id=?", session.getUserId())).isEqualTo(loginUser);
        assertThat(jdbc.queryForMap("SELECT * FROM user_sessions WHERE id=?", session.getId())).isEqualTo(loginSession);
    }
    @ParameterizedTest
    @ValueSource(strings = {"blocked", "revoked", "expired", "moved", "business"})
    void revalidatesDataLoadedBeforeWaitingForTheBusinessLock(String change) throws Exception {
        long customer = customerId(alice);
        var initialSession = sessions.validateSession(alice.getValue(), SessionScope.CUSTOMER);
        var release = new java.util.concurrent.CountDownLatch(1);
        var holder = new java.util.concurrent.CompletableFuture<Integer>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var mutation = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        jdbc.execute("SET LOCAL lock_timeout='20s'");
                        jdbc.execute("SET LOCAL statement_timeout='25s'");
                        jdbc.queryForObject("SELECT id FROM businesses WHERE id=? FOR UPDATE", Long.class, businessId);
                        holder.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        try {
                            if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Mutation was not released");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                        switch (change) {
                            case "blocked" -> jdbc.update("UPDATE customers SET public_booking_blocked=true,public_booking_block_reason='Private' WHERE id=?", customer);
                            case "revoked" -> jdbc.update("UPDATE user_sessions SET revoked_at=NOW() WHERE id=?", initialSession.getId());
                            case "expired" -> jdbc.update("UPDATE user_sessions SET expires_at=NOW()-INTERVAL '1 day' WHERE id=?", initialSession.getId());
                            case "moved" -> jdbc.update("UPDATE customers SET business_id=? WHERE id=?", otherBusinessId, customer);
                            default -> jdbc.update("UPDATE businesses SET status='INACTIVE' WHERE id=?", businessId);
                        }
                    }));
            int pid = holder.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var booking = executor.submit(() -> authenticated.createCustomerAppointment(slug, alice.getValue(), request));
            try {
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))", Integer.class, pid) > 0);
                assertThat(booking.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            mutation.get(10, java.util.concurrent.TimeUnit.SECONDS);
            Class<? extends Throwable> expected = switch (change) {
                case "blocked" -> com.turnero.api.exception.CustomerBookingBlockedException.class;
                case "revoked", "expired" -> com.turnero.api.exception.UnauthorizedException.class;
                default -> com.turnero.api.exception.ForbiddenException.class;
            };
            assertThatThrownBy(() -> booking.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(expected);
            assertThat(count("appointments")).isZero();
            assertThat(tokenCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE id=? AND user_id=?", Long.class,
                    customer, initialSession.getUserId())).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private long business(String businessSlug) {
        return jdbc.queryForObject("""
                INSERT INTO businesses (name, slug, timezone, status, onboarding_status, created_at, updated_at)
                VALUES ('Customer test', ?, 'UTC', 'ACTIVE', 'READY', NOW(), NOW()) RETURNING id
                """, Long.class, businessSlug);
    }

    private PublicAppointmentRequestDto bookingFixture(long targetBusinessId) {
        LocalDateTime start = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2).atTime(12, 0);
        jdbc.update("""
                INSERT INTO booking_settings (business_id, public_booking_enabled, requires_customer_login,
                    booking_window_days, min_notice_hours, cancellation_notice_hours, slot_interval_minutes,
                    manual_confirmation_enabled, whatsapp_reminders_enabled, created_at, updated_at)
                VALUES (?, true, false, 30, 0, 0, 30, false, false, NOW(), NOW())
                """, targetBusinessId);
        long staff = jdbc.queryForObject("""
                INSERT INTO staff_members (business_id, name, status, created_at, updated_at)
                VALUES (?, 'Professional', 'ACTIVE', NOW(), NOW()) RETURNING id
                """, Long.class, targetBusinessId);
        long offering = jdbc.queryForObject("""
                INSERT INTO service_offerings (business_id, name, duration_minutes, price_cents, status, created_at, updated_at)
                VALUES (?, 'Service', 30, 1000, 'ACTIVE', NOW(), NOW()) RETURNING id
                """, Long.class, targetBusinessId);
        jdbc.update("INSERT INTO staff_service_offerings (staff_member_id, service_offering_id) VALUES (?, ?)", staff, offering);
        jdbc.update("""
                INSERT INTO business_hours (business_id, day_of_week, opens_at, closes_at, is_closed)
                VALUES (?, ?, '09:00', '18:00', false)
                """, targetBusinessId, start.getDayOfWeek().name());
        jdbc.update("""
                INSERT INTO staff_working_hours (staff_member_id, day_of_week, starts_at, ends_at, is_available)
                VALUES (?, ?, '09:00', '18:00', true)
                """, staff, start.getDayOfWeek().name());
        var guest = new PublicAppointmentCustomerRequestDto();
        guest.setName("Guest booking");
        guest.setEmail(email);
        guest.setPhoneNumber("123456789");
        var request = new PublicAppointmentRequestDto();
        request.setServiceOfferingId(offering);
        request.setStaffMemberId(Long.toString(staff));
        request.setStartsAt(start);
        request.setCustomer(guest);
        return request;
    }
}

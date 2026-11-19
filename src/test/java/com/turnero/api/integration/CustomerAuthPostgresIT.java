package com.turnero.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.dto.PublicAppointmentCustomerRequestDto;
import com.turnero.api.dto.PublicAppointmentRequestDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.Customer;
import com.turnero.api.model.enums.CustomerStatus;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.service.CustomerAuthService;
import com.turnero.api.service.PublicBookingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.turnero.api.service.GoogleIdentityService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerAuthPostgresIT {
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
    @Autowired CustomerRepository customers;
    @Autowired CustomerAuthService auth;
    @Autowired PublicBookingService booking;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SessionProperties properties;
    @Autowired com.turnero.api.service.SessionService sessions;
    @MockitoBean GoogleIdentityService google;

    String slug;
    String otherSlug;
    String subject;
    String email;
    long businessId;
    long otherBusinessId;

    @BeforeEach
    void setup() {
        String fixture = UUID.randomUUID().toString();
        slug = "client-" + fixture;
        otherSlug = "other-" + fixture;
        subject = "sub-" + fixture;
        email = fixture + "@example.test";
        businessId = business(slug);
        otherBusinessId = business(otherSlug);
        given(google.verify("token")).willReturn(new GoogleIdentityDto(subject, email, true));
    }

    @Test
    void createsOneGlobalIdentityWithTwoBusinessProfilesAndSeparateCookies() throws Exception {
        Cookie first = login(slug);
        Cookie second = login(otherSlug);
        assertThat(first.getName()).isEqualTo(properties.getCustomerCookieName());
        assertThat(first.isHttpOnly()).isTrue();
        assertThat(first.getPath()).isEqualTo("/");
        assertThat(first.getMaxAge()).isEqualTo(properties.getTtlDays() * 86400);
        assertThat(first.getSecure()).isFalse();
        assertThat(countUsers()).isEqualTo(1);
        assertThat(profiles()).isEqualTo(2);
        assertThat(customer(businessId).getId()).isNotEqualTo(customer(otherBusinessId).getId());
        assertThat(customer(businessId).getUserId()).isEqualTo(customer(otherBusinessId).getUserId());
        mvc.perform(get(path(slug, "me")).cookie(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.business.slug").value(slug))
                .andExpect(jsonPath("$.customer.internal_notes").doesNotExist())
                .andExpect(jsonPath("$.customer.public_booking_block_reason").doesNotExist());
        mvc.perform(get(path(otherSlug, "me")).cookie(first)).andExpect(status().isForbidden());
        mvc.perform(post(path(slug, "logout")).cookie(second)).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get(path(otherSlug, "me")).cookie(second)).andExpect(status().isOk());
        given(google.verify("token")).willReturn(new GoogleIdentityDto(subject, "changed@example.test", true));
        login(slug);
        assertThat(profiles()).isEqualTo(2);
        assertThat(customer(businessId).getEmail()).isEqualTo(email);
    }

    @Test
    void ownerUsesTheSameIdentityForCustomerAccessAndAdminTokensCannotCrossScopes() throws Exception {
        jdbc.update("""
                INSERT INTO users (business_id, name, email, auth_provider, auth_subject, role, created_at, updated_at)
                VALUES (?, 'Owner', ?, 'GOOGLE', ?, 'OWNER', NOW(), NOW())
                """, businessId, email, subject);
        Cookie client = login(otherSlug);
        Cookie admin = mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"token\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andReturn().getResponse().getCookie(properties.getCookieName());
        assertThat(countUsers()).isEqualTo(1);
        mvc.perform(get("/api/v1/business").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/business").cookie(client)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/business").cookie(new Cookie(properties.getCookieName(), client.getValue())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path(otherSlug, "me")).cookie(new Cookie(properties.getCustomerCookieName(), admin.getValue())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(properties.getCookieName(), client.getValue())))
                .andExpect(status().isNoContent());
        mvc.perform(get(path(otherSlug, "me")).cookie(client)).andExpect(status().isOk());
        mvc.perform(post(path(otherSlug, "logout")).cookie(new Cookie(properties.getCustomerCookieName(), admin.getValue())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void attachesUniqueGuestPreservingDataReservationsAndBlocks() throws Exception {
        var request = bookingFixture();
        var reservation = booking.createPublicAppointment(slug, request);
        Customer guest = customers.findAllByBusinessId(businessId).getFirst();
        guest.setEmail(" " + email.toUpperCase(java.util.Locale.ROOT) + " ");
        guest.setStatus(CustomerStatus.INACTIVE);
        guest.setPublicBookingBlocked(true);
        guest.setPublicBookingBlockReason("Private reason");
        guest.setInternalNotes("Private notes");
        customers.saveAndFlush(guest);
        LocalDateTime updatedAt = customers.findById(guest.getId()).orElseThrow().getUpdatedAt();

        Cookie cookie = login(slug);
        mvc.perform(get(path(slug, "me")).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.id").value(guest.getId()))
                .andExpect(jsonPath("$.customer.status").value("INACTIVE"))
                .andExpect(jsonPath("$.customer.public_booking_blocked").value(true));
        Customer linked = customers.findById(guest.getId()).orElseThrow();
        assertThat(linked.getName()).isEqualTo(guest.getName());
        assertThat(linked.getPhoneNumber()).isEqualTo(guest.getPhoneNumber());
        assertThat(linked.getEmail()).isEqualTo(guest.getEmail());
        assertThat(linked.getInternalNotes()).isEqualTo("Private notes");
        assertThat(linked.getPublicBookingBlockReason()).isEqualTo("Private reason");
        assertThat(linked.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM appointments WHERE id = ?", Long.class,
                reservation.getAppointmentId())).isEqualTo(guest.getId());
        request.setStartsAt(request.getStartsAt().plusHours(1));
        assertThatThrownBy(() -> booking.createPublicAppointment(slug, request))
                .isInstanceOf(com.turnero.api.exception.CustomerBookingBlockedException.class);
    }

    @Test
    void ambiguousEmailDoesNotCreateIdentityOrChooseGuest() throws Exception {
        guest(businessId, null);
        guest(businessId, null);
        mvc.perform(post(path(slug, "google")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"token\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_IDENTITY_AMBIGUOUS"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(countUsers()).isZero();
        assertThat(customers.findAllByBusinessId(businessId)).allMatch(c -> c.getUserId() == null);
    }

    @Test
    void emailLinkedToAnotherIdentityDoesNotOverwriteOrCreateUser() throws Exception {
        jdbc.update("""
                INSERT INTO users (name, email, auth_provider, auth_subject, role, created_at, updated_at)
                VALUES ('Other identity', ?, 'GOOGLE', ?, 'CUSTOMER', NOW(), NOW())
                """, email, "other-" + subject);
        Long otherUser = jdbc.queryForObject("SELECT id FROM users WHERE auth_subject = ?", Long.class, "other-" + subject);
        Customer existing = guest(businessId, otherUser);
        mvc.perform(post(path(slug, "google")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"token\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_IDENTITY_CONFLICT"));
        assertThat(countUsers()).isZero();
        assertThat(customers.findById(existing.getId()).orElseThrow().getUserId()).isEqualTo(otherUser);
    }

    @Test
    void verifiedEmailAndValidGoogleTokenAreRequiredBeforeAnyWrites() throws Exception {
        given(google.verify("token")).willReturn(new GoogleIdentityDto(subject, email, false));
        mvc.perform(post(path(slug, "google")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"token\"}")).andExpect(status().isUnauthorized());
        given(google.verify("invalid")).willThrow(new UnauthorizedException("Invalid Google ID token"));
        mvc.perform(post(path(slug, "google")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"invalid\"}")).andExpect(status().isUnauthorized());
        assertThat(countUsers()).isZero();
        assertThat(profiles()).isZero();
    }

    @Test
    void logoutRevokesOnlyCurrentSessionAndExpirationIsEnforced() throws Exception {
        Cookie cookie = login(slug);
        Cookie second = login(slug);
        mvc.perform(post(path(slug, "logout")).cookie(cookie)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(properties.getCustomerCookieName(), 0));
        mvc.perform(get(path(slug, "me")).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post(path(slug, "logout")).cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post(path(slug, "logout"))).andExpect(status().isNoContent());
        mvc.perform(get(path(slug, "me")).cookie(second)).andExpect(status().isOk());
        jdbc.update("UPDATE user_sessions SET expires_at = NOW() - INTERVAL '1 second' WHERE user_id = ?",
                customer(businessId).getUserId());
        mvc.perform(get(path(slug, "me")).cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(post(path(slug, "logout")).cookie(second)).andExpect(status().isNoContent());
    }

    @Test
    void failedProvisioningRollsBackIdentityProfileAndSession() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auth.login(slug, "token", null, null);
            throw new IllegalStateException("Rollback after session creation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(countUsers()).isZero();
        assertThat(profiles()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void firstLoginsSerializeWithoutDuplicateIdentitiesOrProfiles(boolean differentBusinesses) throws Exception {
        runContending(() -> auth.login(slug, "token", null, null),
                () -> auth.login(differentBusinesses ? otherSlug : slug, "token", null, null));
        assertThat(countUsers()).isEqualTo(1);
        assertThat(profiles()).isEqualTo(differentBusinesses ? 2 : 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions WHERE user_id = ?",
                Long.class, customer(businessId).getUserId())).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginAndGuestReservationUseOneProfileInEitherOrder(boolean loginFirst) throws Exception {
        var request = bookingFixture();
        Callable<?> login = () -> auth.login(slug, "token", null, null);
        Callable<?> reserve = () -> booking.createPublicAppointment(slug, request);
        runContending(loginFirst ? login : reserve, loginFirst ? reserve : login);
        Customer linked = customer(businessId);
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM appointments WHERE business_id = ?", Long.class, businessId))
                .isEqualTo(linked.getId());
    }

    @Test
    void concurrentAdminCreationCannotDuplicateTheProfileCreatedByLogin() throws Exception {
        Cookie owner = ownerCookie();
        runContending(() -> auth.login(slug, "token", null, null), () -> {
            mvc.perform(post("/api/v1/customers").cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", "Admin guest", "email", email,
                                    "phone_number", "123456789"))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_IDENTITY_CONFLICT"));
            return null;
        });
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(1);
    }

    @Test
    void otherBusinessMatchesNeverAffectResolutionAndInactiveBusinessRejectsAccess() throws Exception {
        guest(otherBusinessId, null);
        guest(otherBusinessId, null);
        Cookie cookie = login(slug);
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(1);
        jdbc.update("UPDATE businesses SET status = 'INACTIVE' WHERE id = ?", businessId);
        mvc.perform(get(path(slug, "me")).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post(path(slug, "google")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"token\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path(slug, "logout")).cookie(cookie)).andExpect(status().isNoContent());
    }

    @Test
    void stableLinkPrecedesAmbiguousEmailAndWrongIdentityCannotUseTheSessionProfile() throws Exception {
        Cookie cookie = login(slug);
        guest(businessId, null);
        login(slug);
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(2);
        assertThat(countUsers()).isEqualTo(1);
        Long wrongUser = jdbc.queryForObject("""
                INSERT INTO users (name,email,auth_provider,auth_subject,role,created_at,updated_at)
                VALUES ('Other', 'other@example.test', 'GOOGLE', ?, 'CUSTOMER', NOW(), NOW()) RETURNING id
                """, Long.class, "wrong-" + subject);
        assertThatThrownBy(() -> sessions.createCustomerSession(wrongUser, customer(businessId).getId(), null, null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(get(path(slug, "me")).cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void adminCreationBeforeLoginIsRecoveredAsTheSameGuest() throws Exception {
        Cookie owner = ownerCookie();
        runContending(() -> {
            mvc.perform(post("/api/v1/customers").cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", "Admin guest", "email", email,
                                    "phone_number", "123456789"))))
                    .andExpect(status().isCreated());
            return null;
        }, () -> auth.login(slug, "token", null, null));
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(1);
        assertThat(customer(businessId).getName()).isEqualTo("Admin guest");
        assertThat(customer(businessId).getUserId()).isNotNull();
    }

    @Test
    void concurrentAdminUpdateKeepsTheIdentityLinkedByLogin() throws Exception {
        Cookie owner = ownerCookie();
        Customer guest = guest(businessId, null);
        runContending(() -> auth.login(slug, "token", null, null), () -> {
            mvc.perform(patch("/api/v1/customers/{id}", guest.getId()).cookie(owner)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Edited by admin\"}"))
                    .andExpect(status().isOk());
            return null;
        });
        Customer linked = customers.findById(guest.getId()).orElseThrow();
        assertThat(linked.getUserId()).isNotNull();
        assertThat(linked.getName()).isEqualTo("Edited by admin");
    }

    @Test
    void httpsUsesADistinctHostPrefixedSecureCookie() throws Exception {
        properties.setSecure(true);
        try {
            Cookie cookie = login(slug);
            assertThat(cookie.getName()).isEqualTo("__Host-turnero_customer_session");
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getDomain()).isNull();
            assertThat(cookie.getPath()).isEqualTo("/");
        } finally {
            properties.setSecure(false);
        }
    }

    @Test
    void competingIdentitiesWithTheSameEmailCannotOverwriteTheFirstLink() throws Exception {
        String otherSubject = "competing-" + subject;
        given(google.verify("competing-token")).willReturn(new GoogleIdentityDto(otherSubject, email, true));
        runContending(() -> auth.login(slug, "token", null, null), () -> {
            assertThatThrownBy(() -> auth.login(slug, "competing-token", null, null))
                    .isInstanceOf(com.turnero.api.exception.CustomerIdentityConflictException.class);
            return null;
        });
        assertThat(profiles()).isEqualTo(1);
        assertThat(countUsers()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE auth_subject = ?", Long.class, otherSubject))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions WHERE user_id = ?",
                Long.class, customer(businessId).getUserId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void adminEmailChangesPreserveSharedContactsAndExistingIdentityLinks(boolean linkedTarget) throws Exception {
        Cookie owner = ownerCookie();
        Cookie original = login(slug);
        long originalId = customer(businessId).getId();
        Long originalUser = customer(businessId).getUserId();
        String otherEmail = "other-" + email;
        long targetId;
        Long targetUser = null;
        if (linkedTarget) {
            given(google.verify("other-token")).willReturn(new GoogleIdentityDto("other-" + subject, otherEmail, true));
            var otherLogin = auth.login(slug, "other-token", null, null);
            targetId = otherLogin.response().customer().id();
            targetUser = customers.findById(targetId).orElseThrow().getUserId();
        } else {
            Customer target = guest(businessId, null);
            target.setEmail(otherEmail);
            targetId = customers.saveAndFlush(target).getId();
        }

        mvc.perform(patch("/api/v1/customers/{id}", targetId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        assertThat(customers.findById(targetId).orElseThrow().getUserId()).isEqualTo(targetUser);
        assertThat(customers.findById(originalId).orElseThrow().getUserId()).isEqualTo(originalUser);
        mvc.perform(get(path(slug, "me")).cookie(original)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.id").value(originalId));
        assertThat(auth.login(slug, "token", null, null).response().customer().id()).isEqualTo(originalId);
    }

    private void runContending(Callable<?> first, Callable<?> second) throws Exception {
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstPid = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET LOCAL statement_timeout = '25s'");
                firstPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                try {
                    Object result = first.call();
                    ready.countDown();
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Release timed out");
                    return result;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            Future<?> b = executor.submit(second);
            try {
                await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                        Integer.class, firstPid.get()) > 0);
                assertThat(b.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Cookie ownerCookie() {
        jdbc.update("""
                INSERT INTO users (business_id, name, email, auth_provider, auth_subject, role, created_at, updated_at)
                VALUES (?, 'Admin', 'admin@example.test', 'GOOGLE', ?, 'OWNER', NOW(), NOW())
                """, businessId, "admin-" + subject);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE auth_subject = ?", Long.class, "admin-" + subject);
        // Exercise the real admin session writer.
        return new Cookie(properties.getCookieName(), sessions.createSession(id,
                com.turnero.api.model.enums.SessionScope.ADMIN, "127.0.0.1", "test"));
    }

    private Cookie login(String businessSlug) throws Exception {
        return mvc.perform(post(path(businessSlug, "google")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_token\":\"token\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")))
                .andReturn().getResponse().getCookie(properties.getCustomerCookieName());
    }

    private static String path(String slug, String endpoint) {
        return "/api/v1/customer/businesses/" + slug + "/auth/" + endpoint;
    }

    private long business(String businessSlug) {
        return jdbc.queryForObject("""
                INSERT INTO businesses (name, slug, timezone, status, onboarding_status, created_at, updated_at)
                VALUES ('Customer test', ?, 'UTC', 'ACTIVE', 'READY', NOW(), NOW()) RETURNING id
                """, Long.class, businessSlug);
    }

    private Customer guest(long business, Long userId) {
        return customers.saveAndFlush(Customer.builder().businessId(business).userId(userId).name("Guest")
                .email(email).phoneNumber("123456789").status(CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Customer customer(long business) {
        return customers.findAllByBusinessId(business).getFirst();
    }

    private long profiles() {
        return jdbc.queryForObject("SELECT count(*) FROM customers WHERE business_id IN (?, ?)",
                Long.class, businessId, otherBusinessId);
    }

    private long countUsers() {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE auth_subject = ?", Long.class, subject);
    }

    private PublicAppointmentRequestDto bookingFixture() {
        LocalDateTime start = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2).atTime(12, 0);
        jdbc.update("""
                INSERT INTO booking_settings (business_id, public_booking_enabled, requires_customer_login,
                    booking_window_days, min_notice_hours, cancellation_notice_hours, slot_interval_minutes,
                    manual_confirmation_enabled, whatsapp_reminders_enabled, created_at, updated_at)
                VALUES (?, true, false, 30, 0, 0, 30, false, false, NOW(), NOW())
                """, businessId);
        long staff = jdbc.queryForObject("""
                INSERT INTO staff_members (business_id, name, status, created_at, updated_at)
                VALUES (?, 'Professional', 'ACTIVE', NOW(), NOW()) RETURNING id
                """, Long.class, businessId);
        long offering = jdbc.queryForObject("""
                INSERT INTO service_offerings (business_id, name, duration_minutes, price_cents, status, created_at, updated_at)
                VALUES (?, 'Service', 30, 1000, 'ACTIVE', NOW(), NOW()) RETURNING id
                """, Long.class, businessId);
        jdbc.update("INSERT INTO staff_service_offerings (staff_member_id, service_offering_id) VALUES (?, ?)", staff, offering);
        jdbc.update("""
                INSERT INTO business_hours (business_id, day_of_week, opens_at, closes_at, is_closed)
                VALUES (?, ?, '09:00', '18:00', false)
                """, businessId, start.getDayOfWeek().name());
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

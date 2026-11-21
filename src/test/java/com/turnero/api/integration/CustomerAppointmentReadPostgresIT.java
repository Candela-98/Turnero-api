package com.turnero.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.service.CustomerAuthService;
import com.turnero.api.service.GoogleIdentityService;
import com.turnero.api.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.turnero.api.model.enums.SessionScope.ADMIN;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerAppointmentReadPostgresIT {
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
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionService sessions;
    @Autowired SessionProperties properties;
    @Autowired CustomerAuthService auth;
    @MockitoBean GoogleIdentityService google;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    @Autowired PlatformTransactionManager transactions;

    String slug;
    String otherSlug;
    long business;
    long otherBusiness;
    long user;
    long otherUser;
    long customer;
    long secondCustomer;
    long otherBusinessCustomer;
    long service;
    long staff;
    Cookie client;
    Cookie secondClient;
    Cookie otherBusinessClient;
    Cookie admin;
    LocalDateTime start;

    @BeforeEach
    void setup() {
        slug = "appointments-" + UUID.randomUUID();
        otherSlug = slug + "-other";
        business = business(slug, "America/Argentina/Buenos_Aires");
        otherBusiness = business(otherSlug, "UTC");
        user = user(business, "OWNER");
        otherUser = user(null, "CUSTOMER");
        customer = customer(business, user, "first@example.test");
        secondCustomer = customer(business, otherUser, "second@example.test");
        otherBusinessCustomer = customer(otherBusiness, user, "first@example.test");
        service = service(business, "Public service");
        staff = staff(business, "Public professional");
        client = client(user, customer);
        secondClient = client(otherUser, secondCustomer);
        otherBusinessClient = client(user, otherBusinessCustomer);
        admin = new Cookie(properties.getCookieName(), sessions.createSession(user, ADMIN, null, null));
        start = LocalDateTime.now().plusDays(4).withHour(12).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void emptyAndOutOfRangePagesKeepThePageContract() throws Exception {
        JsonNode empty = list(slug, client, "").get("page");
        assertThat(empty.get("number").asInt()).isZero();
        assertThat(empty.get("size").asInt()).isEqualTo(50);
        assertThat(empty.get("total_elements").asLong()).isZero();
        assertThat(empty.get("total_pages").asInt()).isZero();
        assertThat(list(slug, client, "").get("data")).isEmpty();
        appointment(business, customer, user, service, staff, start, "CONFIRMED");
        JsonNode outside = list(slug, client, "?page=5&size=2");
        assertThat(outside.get("data")).isEmpty();
        assertThat(outside.at("/page/total_elements").asLong()).isEqualTo(1);
        assertThat(outside.at("/page/total_pages").asInt()).isEqualTo(1);
    }

    @Test
    void pagesAndTotalsUseExplicitOwnershipWithStableOrderingAndAllDatesAndStates() throws Exception {
        long old = appointment(business, customer, user, service, staff, start.minusYears(2), "COMPLETED");
        long tiedFirst = appointment(business, customer, user, service, staff, start, "CANCELLED");
        long tiedSecond = appointment(business, customer, user, service, staff, start, "NO_SHOW");
        long latest = appointment(business, customer, user, service, staff, start.plusYears(2), "PENDING");
        appointment(business, secondCustomer, otherUser, service, staff, start.plusYears(3), "CONFIRMED");
        appointment(business, customer, null, service, staff, start.plusYears(4), "CONFIRMED");

        JsonNode first = list(slug, client, "?size=2");
        JsonNode second = list(slug, client, "?page=1&size=2");
        assertThat(ids(first)).containsExactly(latest, tiedFirst);
        assertThat(ids(second)).containsExactly(tiedSecond, old);
        assertThat(first.at("/page/total_elements").asLong()).isEqualTo(4);
        assertThat(first.at("/page/total_pages").asInt()).isEqualTo(2);
        assertThat(ids(list(slug, client, "?sort=starts_at,asc")))
                .containsExactly(old, tiedFirst, tiedSecond, latest);
    }

    @Test
    void identitiesAndBusinessesScopeBothDataAndTotals() throws Exception {
        long a = appointment(business, customer, user, service, staff, start, "CONFIRMED");
        long b = appointment(business, secondCustomer, otherUser, service, staff, start, "CONFIRMED");
        long otherService = service(otherBusiness, "Other service");
        long otherStaff = staff(otherBusiness, "Other professional");
        long c = appointment(otherBusiness, otherBusinessCustomer, user, otherService, otherStaff, start, "CONFIRMED");
        assertThat(ids(list(slug, client, ""))).containsExactly(a);
        assertThat(ids(list(slug, secondClient, ""))).containsExactly(b);
        assertThat(ids(list(otherSlug, otherBusinessClient, ""))).containsExactly(c);
        assertThat(list(slug, client, "").at("/page/total_elements").asLong()).isEqualTo(1);
        mvc.perform(get(path(otherSlug)).cookie(client)).andExpect(status().isForbidden());
        mvc.perform(get(path(slug)).cookie(otherBusinessClient)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid", "blank", "expired", "revoked", "admin", "admin-cookie-only"})
    void rejectsMissingOrInvalidCustomerSessions(String kind) throws Exception {
        var request = get(path(slug));
        switch (kind) {
            case "missing" -> { }
            case "invalid" -> request.cookie(new Cookie(properties.getCustomerCookieName(), "invalid"));
            case "blank" -> request.cookie(new Cookie(properties.getCustomerCookieName(), ""));
            case "admin" -> request.cookie(new Cookie(properties.getCustomerCookieName(), admin.getValue()));
            case "admin-cookie-only" -> request.cookie(admin);
            case "expired" -> {
                jdbc.update("UPDATE user_sessions SET expires_at = LOCALTIMESTAMP - INTERVAL '1 day' WHERE customer_id = ?", customer);
                request.cookie(client);
            }
            case "revoked" -> {
                jdbc.update("UPDATE user_sessions SET revoked_at = LOCALTIMESTAMP WHERE customer_id = ?", customer);
                request.cookie(client);
            }
            default -> throw new AssertionError(kind);
        }
        mvc.perform(request).andExpect(status().isUnauthorized());
    }

    @Test
    void linkedGuestAppointmentsStayExcludedAndAnExplicitOwnerInTheSameProfileIsVisible() throws Exception {
        String guestSlug = slug + "-guest";
        long guestBusiness = business(guestSlug, "UTC");
        long guest = customer(guestBusiness, null, "guest@example.test");
        long guestService = service(guestBusiness, "Guest service");
        long guestStaff = staff(guestBusiness, "Guest professional");
        long invited = appointment(guestBusiness, guest, null, guestService, guestStaff, start, "CONFIRMED");
        given(google.verify("guest-token")).willReturn(new GoogleIdentityDto(slug, "guest@example.test", true));
        var login = auth.login(guestSlug, "guest-token", null, null);
        assertThat(login.response().customer().id()).isEqualTo(guest);
        long linkedUser = jdbc.queryForObject("SELECT user_id FROM customers WHERE id = ?", Long.class, guest);
        Cookie cookie = new Cookie(properties.getCustomerCookieName(), login.token());
        assertThat(ids(list(guestSlug, cookie, ""))).isEmpty();
        long own = appointment(guestBusiness, guest, linkedUser, guestService, guestStaff, start.plusDays(1), "CONFIRMED");
        assertThat(ids(list(guestSlug, cookie, ""))).containsExactly(own);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id = ?", Long.class, invited)).isNull();
    }

    @Test
    void returnsOnlyThePublicProjectionAndPreservesHistoricalReferencesAndTimeZone() throws Exception {
        appointment(business, customer, user, service, staff, start, "CONFIRMED");
        jdbc.update("UPDATE service_offerings SET status='INACTIVE', deleted_at=NOW() WHERE id = ?", service);
        jdbc.update("UPDATE staff_members SET status='INACTIVE', deleted_at=NOW() WHERE id = ?", staff);
        jdbc.update("""
                UPDATE customers SET status='INACTIVE', public_booking_blocked=true,
                    public_booking_block_reason='PRIVATE BLOCK', internal_notes='PRIVATE CUSTOMER' WHERE id = ?
                """, customer);
        JsonNode row = list(slug, client, "").get("data").get(0);
        var fields = new ArrayList<String>();
        row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "service_offering", "staff_member", "starts_at", "ends_at", "status");
        assertThat(row.at("/service_offering/name").asText()).isEqualTo("Public service");
        assertThat(row.at("/staff_member/name").asText()).isEqualTo("Public professional");
        assertThat(row.get("starts_at").asText()).endsWith("-03:00");
        assertThat(row.toString()).doesNotContain("PRIVATE", "@", "phone", "customer_id", "owner_user_id");
    }

    @Test
    void foreignBusinessReferencesNeverExposeTheirNames() throws Exception {
        long foreignService = service(otherBusiness, "FOREIGN PRIVATE SERVICE");
        long foreignStaff = staff(otherBusiness, "FOREIGN PRIVATE PROFESSIONAL");
        appointment(business, customer, user, foreignService, foreignStaff, start, "CONFIRMED");
        JsonNode row = list(slug, client, "").get("data").get(0);
        assertThat(row.at("/service_offering/name").isNull()).isTrue();
        assertThat(row.at("/staff_member/name").isNull()).isTrue();
        assertThat(row.toString()).doesNotContain("FOREIGN PRIVATE");
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,2147483648", "size,0", "size,101", "size,bad",
            "sort,id", "sort,starts_at;drop table appointments", "owner_user_id,1",
            "user_id,1", "customer_id,1", "business_id,1", "date,2026-01-01"})
    void rejectsInvalidOrOwnershipSelectingParameters(String key, String value) throws Exception {
        mvc.perform(get(path(slug)).cookie(client).param(key, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort"})
    void rejectsRepeatedOrBlankPaginationParameters(String key) throws Exception {
        mvc.perform(get(path(slug)).cookie(client).param(key, "0", "1")).andExpect(status().isBadRequest());
        mvc.perform(get(path(slug)).cookie(client).param(key, " ")).andExpect(status().isBadRequest());
    }

    @Test
    void preservesExistingBusinessErrorsAndLogoutBehavior() throws Exception {
        mvc.perform(get(path("missing-" + slug)).cookie(client)).andExpect(status().isNotFound());
        jdbc.update("UPDATE businesses SET status='INACTIVE' WHERE id = ?", business);
        mvc.perform(get(path(slug)).cookie(client)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customer/businesses/{slug}/auth/logout", slug).cookie(client))
                .andExpect(status().isNoContent());
        jdbc.update("UPDATE businesses SET status='ACTIVE' WHERE id = ?", business);
        mvc.perform(get(path(slug)).cookie(client)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsReassignmentBeforeAnyOtherUpdateAndKeepsOwnershipOnValidEdits() throws Exception {
        long id = appointment(business, customer, user, service, staff, start, "PENDING");
        var before = jdbc.queryForMap("SELECT * FROM appointments WHERE id = ?", id);
        mvc.perform(patch("/api/v1/appointments/{id}", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("customerId", secondCustomer, "starts_at", start.plusDays(2).toString(),
                                "internalNotes", "Attempted partial edit"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForMap("SELECT * FROM appointments WHERE id = ?", id)).isEqualTo(before);

        mvc.perform(patch("/api/v1/appointments/{id}", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("customerId", customer, "starts_at", start.plusDays(1).toString(),
                                "owner_user_id", otherUser, "internalNotes", "Admin edit"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/{id}/confirm", id).cookie(admin)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id = ?", Long.class, id)).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT starts_at FROM appointments WHERE id = ?", LocalDateTime.class, id))
                .isEqualTo(start.plusDays(1));
    }

    @Test
    void legacyReassignmentKeepsNullOwnershipAndAdminCreationCannotChooseAnOwner() throws Exception {
        long id = appointment(business, customer, null, service, staff, start, "CONFIRMED");
        mvc.perform(patch("/api/v1/appointments/{id}", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("customerId", secondCustomer)))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id = ?", Long.class, id)).isNull();
        mvc.perform(post("/api/v1/appointments").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("customerId", customer, "serviceOfferingId", service,
                                "staffMemberId", staff, "startsAt", start.plusDays(1).toString(), "durationMinutes", 30,
                                "endsAt", start.plusDays(1).plusMinutes(30).toString(), "priceCents", 1000,
                                "source", "ADMIN", "status", "CONFIRMED", "ownerUserId", user))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments WHERE business_id=? AND owner_user_id IS NOT NULL",
                Long.class, business)).isZero();
    }

    @Test
    void databaseRejectsAnOwnerOutsideTheProfileOrBusiness() {
        assertForeignKeyFailure(() -> appointment(business, customer, otherUser, service, staff, start, "CONFIRMED"));
        assertForeignKeyFailure(() -> appointment(business, otherBusinessCustomer, user, service, staff, start, "CONFIRMED"));
        long id = appointment(business, customer, user, service, staff, start, "CONFIRMED");
        assertForeignKeyFailure(() -> jdbc.update("UPDATE appointments SET customer_id=? WHERE id=?", secondCustomer, id));
        assertForeignKeyFailure(() -> jdbc.update("UPDATE customers SET user_id=NULL WHERE id=?", customer));
    }

    @ParameterizedTest
    @CsvSource({"confirm,PENDING,CONFIRMED", "cancel,CONFIRMED,CANCELLED",
            "complete,CONFIRMED,COMPLETED", "no-show,CONFIRMED,NO_SHOW"})
    void statusTransitionsKeepExplicitOwnership(String action, String before, String after) throws Exception {
        long id = appointment(business, customer, user, service, staff, start, before);
        mvc.perform(post("/api/v1/appointments/{id}/{action}", id, action).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM appointments WHERE id=?", Long.class, id)).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, id)).isEqualTo(after);
    }

    @Test
    void countAndDataShareOneSnapshotWhenAnotherTransactionInsertsAnOwnedAppointment() throws Exception {
        long original = appointment(business, customer, user, service, staff, start, "CONFIRMED");
        var writer = new TransactionTemplate(transactions);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(call -> {
            Object total = call.callRealMethod();
            writer.executeWithoutResult(status ->
                    appointment(business, customer, user, service, staff, start.plusDays(1), "CONFIRMED"));
            return total;
        }).when(named).queryForObject(startsWith("SELECT COUNT(*) FROM appointments a "),
                any(SqlParameterSource.class), eq(Long.class));

        JsonNode response = list(slug, client, "");
        assertThat(response.at("/page/total_elements").asLong()).isEqualTo(1);
        assertThat(ids(response)).containsExactly(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments WHERE business_id=?", Long.class, business))
                .isEqualTo(2);
    }

    private void assertForeignKeyFailure(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        failure -> assertThat(failure.getSQLState()).isEqualTo("23503"));
    }

    private JsonNode list(String businessSlug, Cookie cookie, String query) throws Exception {
        var response = mvc.perform(get(path(businessSlug) + query).cookie(cookie))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private List<Long> ids(JsonNode response) {
        var ids = new ArrayList<Long>();
        response.get("data").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private String path(String businessSlug) {
        return "/api/v1/customer/businesses/" + businessSlug + "/appointments";
    }

    private Cookie client(long identity, long profile) {
        return new Cookie(properties.getCustomerCookieName(), sessions.createCustomerSession(identity, profile, null, null));
    }

    private long business(String name, String zone) {
        return jdbc.queryForObject("""
                INSERT INTO businesses(name,slug,timezone,status,onboarding_status,created_at,updated_at)
                VALUES ('Studio',?,?,'ACTIVE','READY',NOW(),NOW()) RETURNING id
                """, Long.class, name, zone);
    }

    private long user(Long ownerBusiness, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(business_id,name,email,auth_provider,auth_subject,role,created_at,updated_at)
                VALUES (?,'Account','PRIVATE USER EMAIL','GOOGLE',?,?,NOW(),NOW()) RETURNING id
                """, Long.class, ownerBusiness, UUID.randomUUID().toString(), role);
    }

    private long customer(long businessId, Long identity, String email) {
        return jdbc.queryForObject("""
                INSERT INTO customers(business_id,user_id,name,email,phone_number,status,created_at,updated_at)
                VALUES (?,?,'PRIVATE CUSTOMER',?,'PRIVATE PHONE','ACTIVE',NOW(),NOW()) RETURNING id
                """, Long.class, businessId, identity, email);
    }

    private long service(long businessId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO service_offerings(business_id,name,duration_minutes,price_cents,status,created_at,updated_at)
                VALUES (?,?,30,1000,'ACTIVE',NOW(),NOW()) RETURNING id
                """, Long.class, businessId, name);
    }

    private long staff(long businessId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO staff_members(business_id,name,status,created_at,updated_at)
                VALUES (?,?,'ACTIVE',NOW(),NOW()) RETURNING id
                """, Long.class, businessId, name);
    }

    private long appointment(long businessId, long profile, Long owner, long offering, long professional,
                             LocalDateTime date, String state) {
        return jdbc.queryForObject("""
                INSERT INTO appointments(business_id,customer_id,owner_user_id,service_offering_id,staff_member_id,
                    starts_at,ends_at,duration_minutes,price_cents,status,source,customer_notes,internal_notes,
                    cancellation_reason,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,30,1000,?,'PUBLIC_BOOKING','PRIVATE CUSTOMER NOTES','PRIVATE INTERNAL NOTES',
                    'PRIVATE REASON',NOW(),NOW()) RETURNING id
                """, Long.class, businessId, profile, owner, offering, professional, date, date.plusMinutes(30), state);
    }
}

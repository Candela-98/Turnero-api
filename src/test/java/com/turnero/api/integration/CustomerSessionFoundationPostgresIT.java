package com.turnero.api.integration;

import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.service.GoogleIdentityService;
import com.turnero.api.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;

import static com.turnero.api.model.enums.SessionScope.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerSessionFoundationPostgresIT {

    private static final String SUBJECT = "shared-google-subject";
    private static final String LEGACY_TOKEN = "legacy-owner-token";
    private static final String INSERT_SESSION_WITHOUT_SCOPE = """
            INSERT INTO user_sessions (user_id, session_token_hash, created_at, expires_at, last_seen_at)
            VALUES (?, ?, LOCALTIMESTAMP, LOCALTIMESTAMP + INTERVAL '1 day', LOCALTIMESTAMP)
            """;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).target("1004").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        long business = insertBusiness(jdbc, "owner-business");
        insertBusiness(jdbc, "second-business");
        long owner = insertUser(jdbc, business, "OWNER");
        jdbc.update(INSERT_SESSION_WITHOUT_SCOPE, owner, hash(LEGACY_TOKEN));
        jdbc.update("""
                INSERT INTO customers (business_id, name, email, status, created_at, updated_at)
                VALUES (?, 'Guest', 'guest@example.test', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP)
                """, business);

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private SessionService sessionService;
    @MockitoBean private GoogleIdentityService google;

    @Test
    void preservesGuestAndOwnerSessionFromBeforeTheMigration() throws Exception {
        assertThat(jdbc.queryForMap("SELECT business_id, user_id, status FROM customers WHERE email = ?",
                "guest@example.test"))
                .containsEntry("business_id", businessId("owner-business"))
                .containsEntry("user_id", null)
                .containsEntry("status", "ACTIVE");
        assertThat(scopeOf(LEGACY_TOKEN)).isEqualTo("ADMIN");

        mvc.perform(get("/api/v1/auth/me").cookie(adminCookie(LEGACY_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.slug").value("owner-business"));
        mvc.perform(get("/api/v1/business").cookie(adminCookie(LEGACY_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("owner-business"));
    }

    @Test
    void ownerLoginIgnoresCustomerAccountsAndWritesAdminScope() throws Exception {
        long ownerBusiness = businessId("owner-business");
        long secondBusiness = businessId("second-business");
        jdbc.update("""
                INSERT INTO customers (business_id, user_id, name, status, created_at, updated_at)
                VALUES (?, ?, 'Owner as customer', 'ACTIVE', NOW(), NOW()),
                       (?, ?, 'Owner as customer', 'ACTIVE', NOW(), NOW()) ON CONFLICT (business_id, user_id) DO NOTHING
                """, ownerBusiness, ownerId(), secondBusiness, ownerId());
        assertRejected("23505", () -> insertUser(jdbc, secondBusiness, "CUSTOMER"));
        assertRejected("23505", () -> insertUser(jdbc, secondBusiness, "OWNER"));

        given(google.verify("id-token")).willReturn(new GoogleIdentityDto(SUBJECT, "owner@example.test", true));
        Cookie sessionCookie = mvc.perform(post("/api/v1/auth/google")
                        .contentType(APPLICATION_JSON).content("{\"id_token\":\"id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.business.slug").value("owner-business"))
                .andReturn().getResponse().getCookie(sessionProperties.getCookieName());
        assertThat(sessionCookie).isNotNull();
        assertThat(scopeOf(sessionCookie.getValue())).isEqualTo("ADMIN");

        mvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresExplicitScopeAndChecksTheCustomerSessionLink() {
        long owner = ownerId();
        assertRejected("23502", () -> jdbc.update(INSERT_SESSION_WITHOUT_SCOPE, owner, hash("missing-scope")));
        assertRejected("23514", () -> jdbc.update("""
                INSERT INTO user_sessions (user_id, session_token_hash, created_at, expires_at, last_seen_at, scope)
                VALUES (?, 'no-profile', NOW(), NOW() + INTERVAL '1 day', NOW(), 'CUSTOMER')
                """, owner));
    }

    @Test
    void adminEndpointsRejectCustomerScopeEvenForAnOwnerAndLogoutDoesNotRevokeIt() throws Exception {
        long customerId = jdbc.queryForObject("""
                INSERT INTO customers (business_id, user_id, name, status, created_at, updated_at)
                VALUES (?, ?, 'Owner client', 'ACTIVE', NOW(), NOW()) ON CONFLICT (business_id, user_id)
                DO UPDATE SET name = customers.name RETURNING id
                """, Long.class, businessId("second-business"), ownerId());
        Cookie customerTokenAsAdminCookie = adminCookie(
                sessionService.createCustomerSession(ownerId(), customerId, null, null));

        mvc.perform(get("/api/v1/auth/me").cookie(customerTokenAsAdminCookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/business").cookie(customerTokenAsAdminCookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").cookie(customerTokenAsAdminCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(sessionProperties.getCookieName(), 0));

        assertThat(sessionService.validateSession(customerTokenAsAdminCookie.getValue(), CUSTOMER).getRevokedAt())
                .isNull();
    }

    private static void assertRejected(String sqlState, Runnable write) {
        assertThatThrownBy(write::run).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo(sqlState));
    }

    private String scopeOf(String token) {
        return jdbc.queryForObject("SELECT scope FROM user_sessions WHERE session_token_hash = ?",
                String.class, hash(token));
    }

    private long businessId(String slug) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE slug = ?", Long.class, slug);
    }

    private long ownerId() {
        return jdbc.queryForObject("SELECT id FROM users WHERE auth_subject = ? AND role = 'OWNER'", Long.class, SUBJECT);
    }

    private Cookie adminCookie(String token) {
        return new Cookie(sessionProperties.getCookieName(), token);
    }

    private static long insertBusiness(JdbcTemplate jdbc, String slug) {
        return jdbc.queryForObject("""
                INSERT INTO businesses (name, slug, timezone, status, onboarding_status, created_at, updated_at)
                VALUES (?, ?, 'UTC', 'ACTIVE', 'READY', LOCALTIMESTAMP, LOCALTIMESTAMP)
                RETURNING id
                """, Long.class, slug, slug);
    }

    private static long insertUser(JdbcTemplate jdbc, long business, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (business_id, name, email, auth_provider, auth_subject, role, created_at, updated_at)
                VALUES (?, 'Account', 'owner@example.test', 'GOOGLE', ?, ?, LOCALTIMESTAMP, LOCALTIMESTAMP)
                RETURNING id
                """, Long.class, business, SUBJECT, role);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

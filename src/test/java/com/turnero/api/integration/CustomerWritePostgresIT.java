package com.turnero.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.model.Business;
import com.turnero.api.model.Customer;
import com.turnero.api.model.enums.*;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.http.MediaType;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class CustomerWritePostgresIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CustomerRepository customers;
    @Autowired BusinessRepository businesses;
    @Autowired UserRepository users;
    @Autowired SessionService sessions;
    @Autowired SessionProperties sessionProperties;
    Long businessId;
    Cookie owner;

    @BeforeEach void setup() {
        businessId = business();
        owner = new AdminAuthTestHelper(users, sessions, sessionProperties).ownerSessionCookie(businessId);
    }
    Long business() {
        var now = LocalDateTime.now();
        return businesses.saveAndFlush(Business.builder().name("Studio").slug(UUID.randomUUID().toString())
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP).createdAt(now).updatedAt(now).build()).getId();
    }
    Customer customer(String name, String email, String phone, CustomerStatus state, Long business) {
        var now = LocalDateTime.now();
        return customers.saveAndFlush(Customer.builder().businessId(business).name(name).email(email).phoneNumber(phone)
                .status(state).internalNotes("Private note").createdAt(now).updatedAt(now).build());
    }
    Customer customer(String name, String email, String phone, CustomerStatus state) {
        return customer(name, email, phone, state, businessId);
    }
    JsonNode list(String... parameters) throws Exception {
        var request = get("/api/v1/customers").cookie(owner);
        for (int i = 0; i < parameters.length; i += 2) request.param(parameters[i], parameters[i + 1]);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    JsonNode create(String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/customers").cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test void createsWithCanonicalPhoneAndNotesWithoutEmailAndIgnoresReadOnlyFields() throws Exception {
        var result = create("""
                {"name":"  Ana Pérez  ","phone_number":" +54 (11) 5555-1234 ",
                 "internal_notes":"  Prefiere barba.\\nSin perfume.  ","business_id":999,"user_id":999,"status":"INACTIVE"}
                """);
        var saved = customers.findById(result.get("id").asLong()).orElseThrow();
        assertThat(saved.getBusinessId()).isEqualTo(businessId);
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getName()).isEqualTo("Ana Pérez");
        assertThat(saved.getPhoneNumber()).isEqualTo("+54 (11) 5555-1234");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getInternalNotes()).isEqualTo("Prefiere barba.\nSin perfume.");
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(result.get("internal_notes").asText()).isEqualTo(saved.getInternalNotes());
        assertThat(result.has("created_at") && result.has("updated_at")).isTrue();
        mvc.perform(get("/api/v1/customers/{id}", saved.getId()).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.internal_notes").value(saved.getInternalNotes()));
        assertThat(list().at("/data/0").has("internal_notes")).isFalse();
    }

    @Test void acceptsLegacyPhoneAndNormalizesOptionalWhitespace() throws Exception {
        var legacy = create(""" 
                {"name":"Ana","phone":"5555-1234","email":"  ana@example.com  ","internal_notes":"  "}
                """);
        assertThat(legacy.get("phone_number").asText()).isEqualTo("5555-1234");
        assertThat(legacy.get("email").asText()).isEqualTo("ana@example.com");
        assertThat(legacy.has("internal_notes")).isFalse();
        var blank = create(""" 
                {"name":"Bruno","phone_number":"123","email":"  "}
                """);
        assertThat(blank.has("email")).isFalse();
    }

    static Stream<String> invalidCreates() {
        return Stream.of("{}", "{\"name\":\"Ana\"}", "{\"name\":\" \",\"phone_number\":\"123\"}",
                "{\"name\":\"Ana\",\"phone_number\":\" \"}",
                "{\"name\":\"Ana\",\"phone_number\":null}",
                "{\"name\":\"Ana\",\"phone_number\":\"123\",\"email\":\"invalid\"}",
                "{\"name\":\"" + "a".repeat(101) + "\",\"phone_number\":\"123\"}",
                "{\"name\":\"Ana\",\"phone_number\":\"" + "1".repeat(31) + "\"}",
                "{\"name\":\"Ana\",\"phone_number\":\"123\",\"email\":\"" + "a".repeat(140) + "@example.com\"}",
                "{\"name\":\"Ana\",\"phone_number\":\"123\",\"internal_notes\":\"" + "n".repeat(2001) + "\"}");
    }
    @ParameterizedTest @MethodSource("invalidCreates")
    void rejectsInvalidCreates(String body) throws Exception {
        mvc.perform(post("/api/v1/customers").cookie(owner).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(customers.findAllByBusinessId(businessId)).isEmpty();
    }

    static Stream<String> invalidPatches() {
        return Stream.of("{\"name\":\"\"}", "{\"name\":\" \"}", "{\"phone_number\":\"\"}", "{\"phone_number\":\" \"}",
                "{\"email\":\"invalid\"}", "{\"name\":\"" + "n".repeat(101) + "\"}",
                "{\"phone_number\":\"" + "1".repeat(31) + "\"}",
                "{\"email\":\"" + "a".repeat(140) + "@example.com\"}",
                "{\"internal_notes\":\"" + "n".repeat(2001) + "\"}");
    }
    @ParameterizedTest @MethodSource("invalidPatches")
    void rejectsInvalidPatchesWithoutChangingCustomer(String body) throws Exception {
        var own = customer("Ana", "ana@example.com", "123", CustomerStatus.ACTIVE);
        mvc.perform(patch("/api/v1/customers/{id}", own.getId()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isArray());
        assertThat(customers.findById(own.getId()).orElseThrow().getName()).isEqualTo("Ana");
    }

    @Test void patchClearsOptionalsAndKeepsLegacyMissingPhoneAndInactiveStatus() throws Exception {
        var own = customer("Ana", "ana@example.com", null, CustomerStatus.INACTIVE);
        mvc.perform(patch("/api/v1/customers/{id}", own.getId()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"  Ana actualizada  ","email":"","internal_notes":"  "}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ana actualizada"));
        var saved = customers.findById(own.getId()).orElseThrow();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getInternalNotes()).isNull();
        assertThat(saved.getPhoneNumber()).isNull();
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        mvc.perform(patch("/api/v1/customers/{id}", own.getId()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"phone_number":" +54 (11) 123 ","email":" ana@example.com ","internal_notes":" nueva nota "}
                """)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/customers/{id}", own.getId()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"email":null,"phone_number":null,"internal_notes":null,"name":null}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.phone_number").value("+54 (11) 123"))
                .andExpect(jsonPath("$.internal_notes").value("nueva nota"));
    }

    @Test void scopesDetailAndPatchToAuthenticatedBusiness() throws Exception {
        var other = customer("Other", "other@example.com", "123", CustomerStatus.ACTIVE, business());
        mvc.perform(get("/api/v1/customers/{id}", other.getId()).cookie(owner)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/customers/{id}", other.getId()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
                .andExpect(status().isNotFound());
        assertThat(customers.findById(other.getId()).orElseThrow().getName()).isEqualTo("Other");
        mvc.perform(get("/api/v1/customers/{id}", Long.MAX_VALUE).cookie(owner)).andExpect(status().isNotFound());
    }

    @Test void requiresOwnerSessionForWritesAndDetail() throws Exception {
        var own = customer("Ana", null, "123", CustomerStatus.ACTIVE);
        var helper = new AdminAuthTestHelper(users, sessions, sessionProperties);
        var nonOwner = helper.sessionCookie(businessId, UserRole.CUSTOMER);
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana\",\"phone_number\":\"123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/customers").cookie(nonOwner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana\",\"phone_number\":\"123\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customers/{id}", own.getId()).cookie(nonOwner)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/customers/{id}", own.getId()).cookie(nonOwner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
                .andExpect(status().isForbidden());
    }
}

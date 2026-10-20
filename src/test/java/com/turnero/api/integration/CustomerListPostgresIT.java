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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class CustomerListPostgresIT {
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
        if (!java.util.Arrays.asList(parameters).contains("status")) request.param("status", "ALL");
        for (int i = 0; i < parameters.length; i += 2) request.param(parameters[i], parameters[i + 1]);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void filtersTheBusinessAndKeepsContactFieldsWithoutLeakingNotes() throws Exception {
        var own = customer("Ana", "ana@example.com", "+54 (11) 5555-1234", CustomerStatus.ACTIVE);
        customer("Ana", "ana@example.com", "55551234", CustomerStatus.ACTIVE, business());
        var result = list("q", "ANA");
        assertThat(result.at("/page/total_elements").asInt()).isEqualTo(1);
        assertThat(result.at("/page/size").asInt()).isEqualTo(20);
        var row = result.at("/data/0");
        assertThat(row.get("id").asLong()).isEqualTo(own.getId());
        assertThat(row.get("email").asText()).isEqualTo("ana@example.com");
        assertThat(row.get("phone_number").asText()).isEqualTo(own.getPhoneNumber());
        assertThat(row.has("internal_notes") || row.has("created_at") || row.has("updated_at")).isFalse();
    }
    @Test void searchesEmailCaseInsensitivelyAndCombinesStatus() throws Exception {
        customer("Ana", "HELLO@EXAMPLE.COM", null, CustomerStatus.ACTIVE);
        var inactive = customer("Bruno", "hello@example.com", null, CustomerStatus.INACTIVE);
        var result = list("q", "Lo@eXaMpLe", "status", "INACTIVE");
        assertThat(result.at("/page/total_elements").asInt()).isEqualTo(1);
        assertThat(result.at("/data/0/id").asLong()).isEqualTo(inactive.getId());
    }
    @ParameterizedTest
    @ValueSource(strings = {"55551234", "5555-1234", "(11) 5555 1234", "+54.11.5555.1234"})
    void searchesPhoneIgnoringFormatAndPreservesStoredValue(String search) throws Exception {
        var own = customer("Ana", null, "+54 (11) 5555-1234", CustomerStatus.ACTIVE);
        customer("Bruno", null, null, CustomerStatus.ACTIVE);
        var result = list("q", search);
        assertThat(result.at("/page/total_elements").asInt()).isEqualTo(1);
        assertThat(result.at("/data/0/phone_number").asText()).isEqualTo(own.getPhoneNumber());
        assertThat(customers.findById(own.getId()).orElseThrow().getPhoneNumber()).isEqualTo(own.getPhoneNumber());
    }
    @Test void keepsMixedQueriesLiteralAndEscapesSqlWildcards() throws Exception {
        customer("Ana 5555", null, null, CustomerStatus.ACTIVE);
        customer("Bruno", null, "5555", CustomerStatus.ACTIVE);
        customer("Literal %_\\", null, null, CustomerStatus.ACTIVE);
        assertThat(list("q", "Ana 5555").at("/page/total_elements").asInt()).isEqualTo(1);
        assertThat(list("q", "%_\\").at("/page/total_elements").asInt()).isEqualTo(1);
        assertThat(list("q", "()").at("/page/total_elements").asInt()).isZero();
    }
    @Test void paginatesDuplicateNamesWithStableIdsInBothDirections() throws Exception {
        var a = customer("Ana", null, null, CustomerStatus.ACTIVE);
        var b = customer("Ana", null, null, CustomerStatus.INACTIVE);
        var c = customer("Zoe", null, null, CustomerStatus.ACTIVE);
        var page = list("size", "1", "page", "1");
        assertThat(page.at("/data/0/id").asLong()).isEqualTo(b.getId());
        assertThat(page.at("/page/total_pages").asInt()).isEqualTo(3);
        assertThat(list("size", "1").at("/data/0/id").asLong()).isEqualTo(a.getId());
        assertThat(list("sort", "name,desc", "size", "1").at("/data/0/id").asLong()).isEqualTo(c.getId());
        assertThat(list("sort", "name,desc", "size", "1", "page", "1").at("/data/0/id").asLong()).isEqualTo(a.getId());
        assertThat(list("page", "9").at("/data").isEmpty()).isTrue();
    }
    @Test void returnsAnEmptyPageAndHandlesMissingContacts() throws Exception {
        var empty = list();
        assertThat(empty.at("/data").isEmpty()).isTrue();
        assertThat(empty.at("/page/total_pages").asInt()).isZero();
        customer("Ana", null, null, CustomerStatus.ACTIVE);
        assertThat(list().at("/page/total_elements").asInt()).isEqualTo(1);
        assertThat(list("q", "missing").at("/data").isEmpty()).isTrue();
    }
    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=x", "page=2147483648", "size=0", "size=101", "size=x",
            "status=UNKNOWN", "status=active", "sort=id,asc", "sort=name,up", "sort=name,asc,id,desc"})
    void rejectsInvalidParameters(String pair) throws Exception {
        var parts = pair.split("=", 2);
        mvc.perform(get("/api/v1/customers").cookie(owner).param(parts[0], parts[1])).andExpect(status().isBadRequest());
    }
    @Test void rejectsLongSearchAndRequiresAnOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/customers").cookie(owner).param("q", "x".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        var client = new AdminAuthTestHelper(users, sessions, sessionProperties).sessionCookie(businessId, UserRole.CUSTOMER);
        mvc.perform(get("/api/v1/customers").cookie(client)).andExpect(status().isForbidden());
    }
}

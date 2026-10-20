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
import com.turnero.api.repository.*;
import com.turnero.api.model.*;
import com.turnero.api.service.CustomerService;
import com.turnero.api.exception.CustomerBookingBlockedException;
import com.turnero.api.exception.CustomerIdentityAmbiguousException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
class CustomerArchiveBlockPostgresIT {
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
    @Autowired CustomerService customerService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingSettingsRepository settings;
    @Autowired StaffMemberRepository staff;
    @Autowired ServOfferingRepository services;
    @Autowired StaffServiceOfferingRepository assignments;
    @Autowired BusinessHoursRepository hours;
    @Autowired StaffWorkingHoursRepository working;
    @Autowired AppointmentRepository appointments;
    Long staffId, serviceId;
    LocalDateTime start;

    org.springframework.test.web.servlet.ResultActions patch(Long id, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/{id}", id).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    void block(Customer customer) throws Exception {
        patch(customer.getId(), "{\"public_booking_blocked\":true,\"public_booking_block_reason\":\" Ausencias reiteradas \"}")
                .andExpect(status().isOk());
    }
    void bookingFixture() {
        var now = LocalDateTime.now();
        start = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(2).atTime(12, 0);
        settings.saveAndFlush(BookingSettings.builder().businessId(businessId).publicBookingEnabled(true)
                .bookingWindowDays(30).slotIntervalMinutes(30).createdAt(now).updatedAt(now).build());
        staffId = staff.saveAndFlush(StaffMember.builder().businessId(businessId).name("Profesional")
                .status(StaffMemberStatus.ACTIVE).createdAt(now).updatedAt(now).build()).getId();
        serviceId = services.saveAndFlush(ServiceOffering.builder().businessId(businessId).name("Corte")
                .status(ServiceOfferingStatus.ACTIVE).durationMinutes(30).priceCents(1000).createdAt(now).updatedAt(now).build()).getId();
        assignments.saveAndFlush(StaffServiceOffering.builder().staffMemberId(staffId).serviceOfferingId(serviceId).build());
        var day = com.turnero.api.model.enums.DayOfWeek.valueOf(start.getDayOfWeek().name());
        hours.saveAndFlush(BusinessHours.builder().businessId(businessId).dayOfWeek(day)
                .opensAt(LocalTime.of(9, 0)).closesAt(LocalTime.of(18, 0)).build());
        working.saveAndFlush(StaffWorkingHours.builder().staffMemberId(staffId).dayOfWeek(day).isAvailable(true)
                .startsAt(LocalTime.of(9, 0)).endsAt(LocalTime.of(18, 0)).build());
    }
    org.springframework.test.web.servlet.ResultActions reserve(String email, String phone) throws Exception {
        var slug = businesses.findById(businessId).orElseThrow().getSlug();
        return mvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "service_offering_id", serviceId, "staff_member_id", staffId.toString(), "starts_at", start.toString(),
                        "customer", java.util.Map.of("name", "Visita", "email", email, "phone_number", phone)))));
    }

    @Test void archiveRestoreAndBlockingAreIndependentAndPreservePrivateData() throws Exception {
        var c = customer("Ana", "ana@example.com", "12345", CustomerStatus.ACTIVE);
        block(c);
        mvc.perform(delete("/api/v1/customers/{id}", c.getId()).cookie(owner)).andExpect(status().isNoContent());
        var saved = customers.findById(c.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(saved.isPublicBookingBlocked()).isTrue();
        assertThat(saved.getPublicBookingBlockReason()).isEqualTo("Ausencias reiteradas");
        patch(c.getId(), "{\"status\":\"ACTIVE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.public_booking_blocked").value(true));
        mvc.perform(delete("/api/v1/customers/{id}", c.getId()).cookie(owner)).andExpect(status().isNoContent());
        patch(c.getId(), "{\"public_booking_blocked\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE")).andExpect(jsonPath("$.public_booking_block_reason").doesNotExist());
        assertThat(customers.findById(c.getId()).orElseThrow().getInternalNotes()).isEqualTo("Private note");
    }
    @Test void listDefaultsToUnarchivedAndCombinesBothFiltersWithoutLeakingReasons() throws Exception {
        customer("Ana", "a@example.com", "123", CustomerStatus.ACTIVE);
        var c = customer("Archivada", "b@example.com", "456", CustomerStatus.INACTIVE); block(c);
        customer("Otro negocio", "b@example.com", "456", CustomerStatus.ACTIVE, business());
        mvc.perform(get("/api/v1/customers").cookie(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.page.total_elements").value(1));
        mvc.perform(get("/api/v1/customers").cookie(owner).param("status", "ALL")).andExpect(status().isOk()).andExpect(jsonPath("$.page.total_elements").value(2));
        mvc.perform(get("/api/v1/customers").cookie(owner).param("status", "INACTIVE").param("public_booking_blocked", "true").param("q", "arch"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.total_elements").value(1))
                .andExpect(jsonPath("$.data[0].public_booking_blocked").value(true))
                .andExpect(jsonPath("$.data[0].public_booking_block_reason").doesNotExist());
        mvc.perform(get("/api/v1/customers").cookie(owner).param("public_booking_blocked", "nonsense")).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings = {"", "   "})
    void blockingRequiresNonblankReason(String reason) throws Exception {
        var c = customer("Ana", null, "123", CustomerStatus.ACTIVE);
        patch(c.getId(), json.writeValueAsString(java.util.Map.of("public_booking_blocked", true, "public_booking_block_reason", reason)))
                .andExpect(status().isBadRequest());
        assertThat(customers.findById(c.getId()).orElseThrow().isPublicBookingBlocked()).isFalse();
    }
    @Test void validatesReasonLimitsContactAndScopeWithoutPartialUpdates() throws Exception {
        var c = customer("Ana", null, null, CustomerStatus.ACTIVE);
        patch(c.getId(), "{\"public_booking_blocked\":true,\"public_booking_block_reason\":\"Motivo\",\"name\":\"Changed\"}").andExpect(status().isBadRequest());
        assertThat(customers.findById(c.getId()).orElseThrow().getName()).isEqualTo("Ana");
        patch(c.getId(), json.writeValueAsString(java.util.Map.of("public_booking_blocked", true, "phone_number", "123", "public_booking_block_reason", "x".repeat(501))))
                .andExpect(status().isBadRequest());
        patch(c.getId(), json.writeValueAsString(java.util.Map.of("public_booking_blocked", true, "phone_number", "123", "public_booking_block_reason", "x".repeat(500))))
                .andExpect(status().isOk());
        var other = customer("Other", "x@example.com", "123", CustomerStatus.ACTIVE, business());
        patch(other.getId(), "{\"public_booking_blocked\":true,\"public_booking_block_reason\":\"Motivo\"}").andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/{id}", c.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"public_booking_blocked\":false}"))
                .andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realPublicBookingRejectsEitherContactIncludingSharedArchivedRecords(boolean byEmail) throws Exception {
        bookingFixture();
        var c = customer("Bloqueada", " Ana@Example.com ", "+54 (11) 5555-1234", CustomerStatus.INACTIVE); block(c);
        customer("Duplicada no bloqueada", "other@example.com", "+54 11 5555 1234", CustomerStatus.ACTIVE);
        reserve(byEmail ? "ANA@example.com" : "other@example.com", byEmail ? "99999" : "54-11-5555-1234")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CUSTOMER_BOOKING_BLOCKED"))
                .andExpect(jsonPath("$.message").value("Contact the business to arrange your appointment."));
        assertThat(appointments.findAllByBusinessId(businessId)).isEmpty();
        assertThat(customers.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void successfulPublicBookingRestoresSameCustomerByEmailOrPhone(boolean byEmail) throws Exception {
        bookingFixture();
        var c = customer("Original", byEmail ? "ana@example.com" : null, "1155551234", CustomerStatus.INACTIVE);
        reserve("ANA@example.com", byEmail ? "999999" : "11 5555-1234").andExpect(status().isCreated());
        assertThat(customers.findAllByBusinessId(businessId)).hasSize(1);
        var saved = customers.findById(c.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(saved.getName()).isEqualTo("Original");
        assertThat(saved.getInternalNotes()).isEqualTo("Private note");
        assertThat(appointments.findAllByBusinessId(businessId)).extracting(Appointment::getCustomerId).containsExactly(c.getId());
    }
    @Test void ambiguityDoesNotChooseOrMergeCustomersAndOtherBusinessesDoNotBlock() throws Exception {
        bookingFixture();
        customer("A", "same@example.com", "111", CustomerStatus.INACTIVE);
        customer("B", "same@example.com", "222", CustomerStatus.ACTIVE);
        reserve("same@example.com", "333").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_IDENTITY_AMBIGUOUS"));
        var other = customer("Other", "new@example.com", "333", CustomerStatus.INACTIVE, business());
        other.setPublicBookingBlocked(true); other.setPublicBookingBlockReason("Other business"); customers.saveAndFlush(other);
        reserve("new@example.com", "333").andExpect(status().isCreated());
    }
    @Test void restoresWithinReservationTransactionAndRollsBackWithIt() {
        var c = customer("Ana", null, "123", CustomerStatus.INACTIVE);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            customerService.findOrCreateCustomerForBusiness(businessId, "Otra", "new@example.com", "123");
            throw new IllegalStateException("Later reservation failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(customers.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }
    @Test void publicReservationSeesConcurrentBlockingAfterWaitingForCustomerLock() throws Exception {
        var c = customer("Ana", "a@example.com", "123", CustomerStatus.INACTIVE);
        var loaded = new CountDownLatch(1); var read = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reservation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                customers.findById(c.getId()).orElseThrow(); loaded.countDown();
                try { if (!read.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Gate timed out"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return customerService.findOrCreateCustomerForBusiness(businessId, "Ana", "a@example.com", "123");
            }));
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var locked = customers.findByIdAndBusinessIdForUpdate(c.getId(), businessId).orElseThrow();
                locked.setPublicBookingBlocked(true); locked.setPublicBookingBlockReason("Concurrent block"); customers.saveAndFlush(locked);
                read.countDown();
                await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%customers%'", Integer.class) > 0);
                assertThat(reservation.isDone()).isFalse();
            });
            assertThatThrownBy(() -> reservation.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(CustomerBookingBlockedException.class);
        }
        assertThat(customers.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }
    @Test void administrativeBookingRemainsAllowedAndArchivingKeepsAppointments() throws Exception {
        bookingFixture();
        var c = customer("Ana", "a@example.com", "123", CustomerStatus.ACTIVE); block(c);
        mvc.perform(post("/api/v1/appointments").cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("customerId", c.getId(), "serviceOfferingId", serviceId,
                        "staffMemberId", staffId, "startsAt", start.toString(), "durationMinutes", 30, "endsAt", start.plusMinutes(30).toString(), "source", "ADMIN", "status", "CONFIRMED"))))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/customers/{id}", c.getId()).cookie(owner)).andExpect(status().isNoContent());
        assertThat(appointments.findAllByBusinessId(businessId)).hasSize(1);
    }
}

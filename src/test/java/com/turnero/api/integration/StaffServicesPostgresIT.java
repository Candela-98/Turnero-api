package com.turnero.api.integration;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.exception.InvalidStateTransitionException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.*;
import com.turnero.api.repository.*;
import com.turnero.api.service.StaffServiceOfferingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StaffServicesPostgresIT {
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
    @Autowired StaffServiceOfferingService service;
    @Autowired com.turnero.api.service.StaffMemberService members;
    @Autowired StaffMemberRepository staff;
    @Autowired ServOfferingRepository offerings;
    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired AppointmentRepository appointments;
    @MockitoSpyBean StaffServiceOfferingRepository associations;
    @MockitoBean CurrentBusinessContext context;
    Long businessId;
    Long staffId;
    Long first;
    Long second;
    Long third;
    @BeforeEach void setUp() {
        businessId = business();
        staffId = member(businessId);
        first = offering(businessId, ServiceOfferingStatus.ACTIVE);
        second = offering(businessId, ServiceOfferingStatus.ACTIVE);
        third = offering(businessId, ServiceOfferingStatus.INACTIVE);
        when(context.getCurrentBusinessId()).thenReturn(businessId);
    }
    Long business() {
        var now = LocalDateTime.now();
        return businesses.saveAndFlush(Business.builder().name("Studio").slug(UUID.randomUUID().toString())
                .timezone("UTC").status(BusinessStatus.ACTIVE).onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(now).updatedAt(now).build()).getId();
    }
    Long member(Long business) {
        return staff.saveAndFlush(StaffMember.builder().businessId(business).name("Ana").status(StaffMemberStatus.INACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()).getId();
    }
    Long offering(Long business, ServiceOfferingStatus status) {
        return offerings.saveAndFlush(ServiceOffering.builder().businessId(business).name("Corte")
                .durationMinutes(30).priceCents(2000).status(status).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()).getId();
    }
    List<Long> ids() {
        return associations.findAllByStaffMemberId(staffId).stream().map(StaffServiceOffering::getServiceOfferingId).sorted().toList();
    }
    @Test void replacesSeveralOneAndNoneRetainingRowsAndHistoricalAppointments() {
        service.replaceServiceOfferings(staffId, List.of(first, second, first));
        var retained = associations.findAllByStaffMemberId(staffId).stream().filter(a -> a.getServiceOfferingId().equals(first)).findFirst().orElseThrow();
        var now = LocalDateTime.now().withNano(0);
        var customer = customers.saveAndFlush(Customer.builder().businessId(businessId).name("Cliente").status(CustomerStatus.ACTIVE).createdAt(now).updatedAt(now).build());
        for (var status : List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED)) {
            appointments.saveAndFlush(Appointment.builder().businessId(businessId).customerId(customer.getId()).staffMemberId(staffId)
                    .serviceOfferingId(second).startsAt(now.plusDays(status == AppointmentStatus.COMPLETED ? -1 : 1))
                    .endsAt(now.plusDays(status == AppointmentStatus.COMPLETED ? -1 : 1).plusMinutes(30))
                    .durationMinutes(30).priceCents(2000).status(status).source(AppointmentSource.ADMIN).createdAt(now).updatedAt(now).build());
        }
        var before = appointments.findAll().stream().filter(a -> a.getBusinessId().equals(businessId)).toList();
        service.replaceServiceOfferings(staffId, List.of(first));
        assertThat(ids()).containsExactly(first);
        assertThat(associations.findAllByStaffMemberId(staffId).getFirst().getId()).isEqualTo(retained.getId());
        service.replaceServiceOfferings(staffId, List.of());
        assertThat(ids()).isEmpty();
        assertThat(appointments.findAll().stream().filter(a -> a.getBusinessId().equals(businessId)).toList())
                .usingRecursiveComparison().isEqualTo(before);
    }
    @Test void retainsExistingInactiveButRejectsNewInactiveAndAllowsRemoval() {
        associations.saveAndFlush(StaffServiceOffering.builder().staffMemberId(staffId).serviceOfferingId(third).build());
        service.replaceServiceOfferings(staffId, List.of(first, third));
        assertThat(ids()).containsExactly(first, third);
        service.replaceServiceOfferings(staffId, List.of(first));
        assertThatThrownBy(() -> service.replaceServiceOfferings(staffId, List.of(first, third))).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(ids()).containsExactly(first);
    }
    @Test void scopesStaffAndServicesAndRejectsDeletedServicesBeforeChangingAnything() {
        service.replaceServiceOfferings(staffId, List.of(first));
        var foreignBusiness = business();
        var foreignStaff = member(foreignBusiness);
        var foreignService = offering(foreignBusiness, ServiceOfferingStatus.ACTIVE);
        assertThatThrownBy(() -> service.replaceServiceOfferings(foreignStaff, List.of())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getServiceOfferings(foreignStaff)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.replaceServiceOfferings(staffId, List.of(foreignService))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.replaceServiceOfferings(staffId, List.of(Long.MAX_VALUE))).isInstanceOf(ResourceNotFoundException.class);
        var deleted = offerings.findById(second).orElseThrow();
        deleted.setDeletedAt(LocalDateTime.now());
        offerings.saveAndFlush(deleted);
        assertThatThrownBy(() -> service.replaceServiceOfferings(staffId, List.of(second))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(ids()).containsExactly(first);
    }
    @Test void rollsBackRemovalsWhenInsertFails() {
        service.replaceServiceOfferings(staffId, List.of(first));
        doAnswer(invocation -> {
            associations.flush(); // Exercise rollback after DELETE has actually reached PostgreSQL.
            throw new IllegalStateException("insert failed");
        }).when(associations).saveAll(anyList());
        assertThatThrownBy(() -> service.replaceServiceOfferings(staffId, List.of(second))).hasMessage("insert failed");
        assertThat(ids()).containsExactly(first);
    }
    @Test void serializesConcurrentReplacementsWithoutMixedSelections() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> a = () -> { start.await(); service.replaceServiceOfferings(staffId, List.of(first)); return null; };
            Callable<Void> b = () -> { start.await(); service.replaceServiceOfferings(staffId, List.of(second)); return null; };
            var one = executor.submit(a);
            var two = executor.submit(b);
            start.countDown();
            one.get(15, TimeUnit.SECONDS);
            two.get(15, TimeUnit.SECONDS);
            assertThat(ids()).isIn(List.of(first), List.of(second));
        }
    }
    @Test void readsInactiveAssociationsButHidesRemovedOnes() {
        associations.saveAndFlush(StaffServiceOffering.builder().staffMemberId(staffId).serviceOfferingId(third).build());
        assertThat(service.getServiceOfferings(staffId)).extracting("id").containsExactly(third);
        var deleted = offerings.findById(third).orElseThrow();
        deleted.setDeletedAt(LocalDateTime.now());
        offerings.saveAndFlush(deleted);
        assertThat(service.getServiceOfferings(staffId)).isEmpty();
    }
    @Test void countsOnlyRequestedProfessionalsAndNonRemovedServicesInTheCurrentBusiness() {
        var unassigned = member(businessId);
        service.replaceServiceOfferings(staffId, List.of(first, second));
        associations.saveAndFlush(StaffServiceOffering.builder().staffMemberId(staffId).serviceOfferingId(third).build());
        var removed = offerings.findById(second).orElseThrow();
        removed.setDeletedAt(LocalDateTime.now());
        offerings.saveAndFlush(removed);
        var otherBusiness = business();
        var foreignStaff = member(otherBusiness);
        var foreignService = offering(otherBusiness, ServiceOfferingStatus.ACTIVE);
        associations.saveAndFlush(StaffServiceOffering.builder().staffMemberId(foreignStaff).serviceOfferingId(foreignService).build());
        // Even a legacy cross-business association cannot inflate the visible count.
        associations.saveAndFlush(StaffServiceOffering.builder().staffMemberId(staffId).serviceOfferingId(foreignService).build());
        assertThat(members.getServiceCounts(List.of(staffId, unassigned, foreignStaff))).containsExactlyEntriesOf(java.util.Map.of(staffId, 2L));
        assertThat(members.getServiceCounts(List.of(unassigned))).isEmpty();
        assertThat(service.getServiceOfferings(staffId)).hasSize(2);
        service.replaceServiceOfferings(staffId, List.of());
        assertThat(members.getServiceCounts(List.of(staffId))).isEmpty();
    }
}

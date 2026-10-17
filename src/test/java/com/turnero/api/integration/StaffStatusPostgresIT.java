package com.turnero.api.integration;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.*;
import com.turnero.api.exception.*;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.*;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.*;
import com.turnero.api.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StaffStatusPostgresIT {
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
    @Autowired StaffMemberService members;
    @Autowired AppointmentService admin;
    @Autowired StaffServiceOfferingService staffServices;
    @Autowired PublicBookingService booking;
    @Autowired BusinessRepository businesses;
    @MockitoSpyBean StaffMemberRepository staff;
    @Autowired ServOfferingRepository services;
    @Autowired CustomerRepository customers;
    @Autowired UserRepository users;
    @Autowired StaffServiceOfferingRepository assignments;
    @Autowired StaffWorkingHoursRepository hours;
    @Autowired BusinessHoursRepository businessHours;
    @Autowired BookingSettingsRepository settings;
    @Autowired AppointmentRepository appointments;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean CurrentBusinessContext context;
    @MockitoSpyBean Clock clock;
    Business business;
    StaffMember member;
    Long serviceId, customerId;
    LocalDateTime start;

    @BeforeEach void setup() {
        var now = LocalDateTime.now().withNano(0);
        business = businesses.saveAndFlush(Business.builder().name("Studio").slug(UUID.randomUUID().toString()).timezone("UTC")
                .status(BusinessStatus.ACTIVE).onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP).createdAt(now).updatedAt(now).build());
        var user = users.saveAndFlush(User.builder().businessId(business.getId()).name("Owner").email(UUID.randomUUID()+"@example.com")
                .authProvider(AuthProvider.GOOGLE).authSubject(UUID.randomUUID().toString()).role(UserRole.OWNER).createdAt(now).updatedAt(now).build());
        member = staff.saveAndFlush(StaffMember.builder().businessId(business.getId()).userId(user.getId()).name("Ana").roleLabel("Barbera")
                .specialty("Cortes").status(StaffMemberStatus.ACTIVE).createdAt(now).updatedAt(now).build());
        serviceId = services.saveAndFlush(ServiceOffering.builder().businessId(business.getId()).name("Corte").durationMinutes(30)
                .priceCents(2000).status(ServiceOfferingStatus.ACTIVE).createdAt(now).updatedAt(now).build()).getId();
        customerId = customers.saveAndFlush(Customer.builder().businessId(business.getId()).name("Cliente").email("client@example.com")
                .status(CustomerStatus.ACTIVE).createdAt(now).updatedAt(now).build()).getId();
        assignments.saveAndFlush(StaffServiceOffering.builder().staffMemberId(member.getId()).serviceOfferingId(serviceId).build());
        for (var day : DayOfWeek.values()) {
            businessHours.saveAndFlush(BusinessHours.builder().businessId(business.getId()).dayOfWeek(day).isClosed(false)
                    .opensAt(LocalTime.of(9,0)).closesAt(LocalTime.of(18,0)).build());
            hours.saveAndFlush(StaffWorkingHours.builder().staffMemberId(member.getId()).dayOfWeek(day).isAvailable(true)
                    .startsAt(LocalTime.of(9,0)).endsAt(LocalTime.of(18,0)).build());
        }
        settings.saveAndFlush(BookingSettings.builder().businessId(business.getId()).publicBookingEnabled(true).bookingWindowDays(30)
                .slotIntervalMinutes(30).createdAt(now).updatedAt(now).build());
        start = LocalDate.now().plusDays(3).atTime(13,0);
        when(context.getCurrentBusinessId()).thenReturn(business.getId());
    }
    Appointment appointment(AppointmentStatus status, LocalDateTime begins) {
        return appointments.saveAndFlush(Appointment.builder().businessId(business.getId()).staffMemberId(member.getId()).customerId(customerId)
                .serviceOfferingId(serviceId).startsAt(begins).endsAt(begins.plusMinutes(30)).durationMinutes(30).priceCents(2000)
                .status(status).source(AppointmentSource.ADMIN).createdAt(LocalDateTime.now().withNano(0)).updatedAt(LocalDateTime.now().withNano(0)).build());
    }
    Object reserve(boolean isPublic) {
        if (!isPublic) return admin.saveAppointment(AppointmentRequestDto.builder().staffMemberId(member.getId()).customerId(customerId)
                .serviceOfferingId(serviceId).startsAt(start).endsAt(start.plusMinutes(30)).durationMinutes(30).priceCents(2000)
                .status(AppointmentStatus.CONFIRMED).source(AppointmentSource.ADMIN).build());
        var request = new PublicAppointmentRequestDto();
        request.setServiceOfferingId(serviceId); request.setStaffMemberId(member.getId().toString()); request.setStartsAt(start);
        var customer = new PublicAppointmentCustomerRequestDto(); customer.setName("Cliente"); customer.setEmail("reserve@example.com"); customer.setPhoneNumber("123456789");
        request.setCustomer(customer);
        return booking.createPublicAppointment(business.getSlug(), request);
    }
    @Test void changesStateIdempotentlyAndPreservesRelationsAndHistory() {
        var history = appointment(AppointmentStatus.COMPLETED, start.minusDays(6));
        var links = assignments.findAllByStaffMemberId(member.getId());
        var week = hours.findAllByStaffMemberId(member.getId());
        members.deleteStaffMember(member.getId());
        var inactive = members.findStaffMember(member.getId());
        members.deleteStaffMember(member.getId());
        assertThat(members.findStaffMember(member.getId()).getUpdatedAt()).isEqualTo(inactive.getUpdatedAt());
        assertThat(members.findStaffMemberForUser(member.getUserId())).get().extracting(StaffMember::getStatus).isEqualTo(StaffMemberStatus.INACTIVE);
        members.updateStaffMember(StaffMemberUpdateRequestDto.builder().status(StaffMemberStatus.ACTIVE).build(), member.getId());
        var active = members.findStaffMember(member.getId());
        assertThat(members.updateStaffMember(StaffMemberUpdateRequestDto.builder().status(StaffMemberStatus.ACTIVE).build(), member.getId()).getUpdatedAt()).isEqualTo(active.getUpdatedAt());
        assertThat(active.getUserId()).isEqualTo(member.getUserId());
        assertThat(assignments.findAllByStaffMemberId(member.getId())).usingRecursiveComparison().isEqualTo(links);
        assertThat(hours.findAllByStaffMemberId(member.getId())).usingRecursiveComparison().isEqualTo(week);
        assertThat(appointments.findById(history.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(history);
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","CONFIRMED"})
    void rejectsBothDeactivatePathsAndRollsBackMixedPatch(String status) {
        appointment(AppointmentStatus.valueOf(status), start);
        assertThatThrownBy(() -> members.deleteStaffMember(member.getId())).isInstanceOf(StaffHasFutureAppointmentsException.class);
        assertThatThrownBy(() -> members.updateStaffMember(StaffMemberUpdateRequestDto.builder().name("Changed")
                .status(StaffMemberStatus.INACTIVE).build(), member.getId())).isInstanceOf(StaffHasFutureAppointmentsException.class);
        assertThat(members.findStaffMember(member.getId())).extracting(StaffMember::getName, StaffMember::getStatus).containsExactly("Ana", StaffMemberStatus.ACTIVE);
    }
    @ParameterizedTest @CsvSource({"CANCELLED,3","COMPLETED,3","NO_SHOW,3","PENDING,-3","CONFIRMED,-3"})
    void ignoresNonBlockingAppointments(AppointmentStatus status, int days) {
        appointment(status, LocalDateTime.now().plusDays(days));
        members.deleteStaffMember(member.getId());
        assertThat(members.findStaffMember(member.getId()).getStatus()).isEqualTo(StaffMemberStatus.INACTIVE);
    }
    @Test void evaluatesFutureInBusinessTimezoneAndScopesAllStateChanges() {
        business.setTimezone("America/Argentina/Buenos_Aires"); businesses.saveAndFlush(business);
        var instant = Instant.parse("2026-10-17T12:00:00Z");
        doAnswer(call -> Clock.fixed(instant, call.getArgument(0))).when(clock).withZone(any(ZoneId.class));
        appointment(AppointmentStatus.PENDING, LocalDateTime.of(2026,10,17,10,0)); // 09:00 in the business, but 12:00 UTC
        assertThatThrownBy(() -> members.deleteStaffMember(member.getId())).isInstanceOf(StaffHasFutureAppointmentsException.class);
        when(context.getCurrentBusinessId()).thenReturn(-1L);
        assertThatThrownBy(() -> members.findStaffMember(member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> members.deleteStaffMember(member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> members.updateStaffMember(StaffMemberUpdateRequestDto.builder().status(StaffMemberStatus.ACTIVE).build(),member.getId())).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void rejectsAdminReprogrammingButAllowsNotesForInactiveHistoricalProfessional() {
        var history = appointment(AppointmentStatus.COMPLETED, start.minusDays(6));
        members.deleteStaffMember(member.getId());
        assertThatThrownBy(() -> admin.updateAppointment(history.getId(),AppointmentUpdateRequestDto.builder().startsAt(start).build())).isInstanceOf(InvalidStateTransitionException.class);
        admin.updateAppointment(history.getId(),AppointmentUpdateRequestDto.builder().internalNotes("Historical note").build());
        assertThat(appointments.findById(history.getId()).orElseThrow().getInternalNotes()).isEqualTo("Historical note");
    }
    @Test void rejectsReassignmentToInactiveStaffWithoutChangingTheAppointment() {
        var other = staff.saveAndFlush(StaffMember.builder().businessId(business.getId()).name("Other").status(StaffMemberStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        var future = appointment(AppointmentStatus.CONFIRMED, start);
        future.setStaffMemberId(other.getId()); appointments.saveAndFlush(future);
        members.deleteStaffMember(member.getId());
        assertThatThrownBy(() -> admin.updateAppointment(future.getId(), AppointmentUpdateRequestDto.builder()
                .staffMemberId(member.getId()).internalNotes("Should roll back").build())).isInstanceOf(InvalidStateTransitionException.class);
        var unchanged = appointments.findById(future.getId()).orElseThrow();
        assertThat(unchanged.getStaffMemberId()).isEqualTo(other.getId());
        assertThat(unchanged.getInternalNotes()).isNull();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void deactivationWinsBeforeConcurrentReservation(boolean isPublic) throws Exception {
        race(() -> { members.deleteStaffMember(member.getId()); return null; }, () -> reserve(isPublic), isPublic ? ForbiddenException.class : InvalidStateTransitionException.class);
        assertThat(appointments.findByStaffMemberIdAndBusinessId(member.getId(),business.getId())).isEmpty();
        assertThat(members.findStaffMember(member.getId()).getStatus()).isEqualTo(StaffMemberStatus.INACTIVE);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void reservationWinsAndConcurrentDeactivationConflicts(boolean isPublic) throws Exception {
        race(() -> reserve(isPublic), () -> { members.deleteStaffMember(member.getId()); return null; }, StaffHasFutureAppointmentsException.class);
        assertThat(appointments.findByStaffMemberIdAndBusinessId(member.getId(),business.getId())).hasSize(1);
        assertThat(members.findStaffMember(member.getId()).getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);
    }
    @Test void removesInactiveStaffFromOperationalReadsButPreservesHistoryAndFreesUserLink() {
        var history = appointment(AppointmentStatus.COMPLETED, start.minusDays(6));
        var oldHours = hours.findAllByStaffMemberId(member.getId());
        var oldAssignments = assignments.findAllByStaffMemberId(member.getId());
        members.deleteStaffMember(member.getId()); members.removeFromCatalog(member.getId());
        var archived = staff.findByIdAndBusinessId(member.getId(),business.getId()).orElseThrow();
        assertThat(archived.getDeletedAt()).isNotNull(); assertThat(archived.getUserId()).isEqualTo(member.getUserId());
        assertThat(members.listStaffMembers(null,null,0,20,org.springframework.data.domain.Sort.by("name")).getTotalElements()).isZero();
        assertThat(members.listStaffMembers(null,StaffMemberStatus.INACTIVE,0,20,org.springframework.data.domain.Sort.by("name")).getTotalElements()).isZero();
        assertThat(members.findStaffMemberForUser(member.getUserId())).isEmpty();
        assertThatThrownBy(() -> members.findStaffMember(member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> members.getWorkingHours(member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> staffServices.getServiceOfferings(member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> staffServices.replaceServiceOfferings(member.getId(),List.of())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> members.updateStaffMember(StaffMemberUpdateRequestDto.builder().status(StaffMemberStatus.ACTIVE).build(),member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> members.removeFromCatalog(member.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(hours.findAllByStaffMemberId(member.getId())).usingRecursiveComparison().isEqualTo(oldHours);
        assertThat(assignments.findAllByStaffMemberId(member.getId())).usingRecursiveComparison().isEqualTo(oldAssignments);
        assertThat(appointments.findById(history.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(history);
        var replacement = members.saveStaffMember(StaffMember.builder().userId(member.getUserId()).name("New profile").build());
        assertThat(replacement.getId()).isNotEqualTo(member.getId());
        assertThat(members.findStaffMemberForUser(member.getUserId())).get().extracting(StaffMember::getId).isEqualTo(replacement.getId());
        assertThatThrownBy(() -> members.saveStaffMember(StaffMember.builder().userId(member.getUserId()).name("Duplicate").build())).isInstanceOf(StaffUserAlreadyLinkedException.class);
    }
    @Test void removalRejectsActiveForeignAndFutureAppointmentsWithoutChanges() {
        assertThatThrownBy(() -> members.removeFromCatalog(member.getId())).isInstanceOf(InvalidStateTransitionException.class);
        member.setStatus(StaffMemberStatus.INACTIVE); staff.saveAndFlush(member);
        appointment(AppointmentStatus.CONFIRMED,start);
        assertThatThrownBy(() -> members.removeFromCatalog(member.getId())).isInstanceOf(StaffHasFutureAppointmentsException.class);
        assertThat(staff.findById(member.getId()).orElseThrow().getDeletedAt()).isNull();
        when(context.getCurrentBusinessId()).thenReturn(-1L);
        assertThatThrownBy(() -> members.removeFromCatalog(member.getId())).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void removalWinsBeforeConcurrentReactivation() throws Exception {
        members.deleteStaffMember(member.getId());
        race(() -> { members.removeFromCatalog(member.getId()); return null; }, () -> members.updateStaffMember(
                StaffMemberUpdateRequestDto.builder().status(StaffMemberStatus.ACTIVE).build(),member.getId()),ResourceNotFoundException.class);
        assertThat(staff.findById(member.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }
    @Test void reactivationWinsBeforeConcurrentRemoval() throws Exception {
        members.deleteStaffMember(member.getId());
        race(() -> members.updateStaffMember(StaffMemberUpdateRequestDto.builder().status(StaffMemberStatus.ACTIVE).build(),member.getId()),
                () -> { members.removeFromCatalog(member.getId()); return null; },InvalidStateTransitionException.class);
        assertThat(members.findStaffMember(member.getId()).getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);
    }
    void race(Supplier<Object> winner, Supplier<Object> contender, Class<? extends Throwable> error) throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                var result = winner.get(); held.countDown(); await(release); return result;
            }));
            try {
                if (!held.await(15,TimeUnit.SECONDS)) { first.get(1,TimeUnit.SECONDS); throw new AssertionError("Winner did not acquire the lock"); }
                var second = executor.submit(() -> { Thread.currentThread().setName("turn101-contender"); return contender.get(); });
                verify(staff, timeout(15000).atLeast(2)).findByIdAndBusinessIdForUpdate(member.getId(),business.getId());
                release.countDown(); first.get(15,TimeUnit.SECONDS);
                assertThatThrownBy(() -> second.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(error);
            } finally { release.countDown(); }
        }
    }
    static void await(CountDownLatch latch) { try { if (!latch.await(20,TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
}

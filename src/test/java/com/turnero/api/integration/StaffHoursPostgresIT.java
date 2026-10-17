package com.turnero.api.integration;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.*;
import com.turnero.api.repository.*;
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
class StaffHoursPostgresIT {
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
    @Autowired com.turnero.api.service.StaffMemberService members;
    @Autowired StaffMemberRepository staff;
    @Autowired ServOfferingRepository offerings;
    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired AppointmentRepository appointments;
    @MockitoSpyBean StaffWorkingHoursRepository hours;
    @Autowired BusinessHoursRepository businessHours;
    @Autowired BookingSettingsRepository settings;
    @Autowired com.turnero.api.service.AvailabilityService availability;
    @MockitoBean CurrentBusinessContext context;
    Long businessId;
    Long staffId;
    Long first;
    @BeforeEach void setUp() {
        businessId = business();
        staffId = member(businessId);
        first = offering(businessId, ServiceOfferingStatus.ACTIVE);
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
    List<com.turnero.api.dto.StaffWorkingHoursRequestDto> week(boolean available, int start, int end) {
        return java.util.Arrays.stream(DayOfWeek.values()).map(day -> com.turnero.api.dto.StaffWorkingHoursRequestDto.builder()
                .dayOfWeek(day).isAvailable(available).startsAt(java.time.LocalTime.of(start, 0))
                .endsAt(java.time.LocalTime.of(end, 0)).build()).toList();
    }
    @Test void readsSevenDaysWithoutPersistingMissingOnesAndNormalizesUnavailableTimes() {
        hours.saveAndFlush(StaffWorkingHours.builder().staffMemberId(staffId).dayOfWeek(DayOfWeek.TUESDAY)
                .isAvailable(false).startsAt(java.time.LocalTime.of(9, 0)).endsAt(java.time.LocalTime.of(17, 0)).build());
        var result = members.getWorkingHours(staffId);
        assertThat(result).extracting("dayOfWeek").containsExactly(DayOfWeek.values());
        assertThat(result).allSatisfy(day -> { assertThat(day.isAvailable()).isFalse(); assertThat(day.getStartsAt()).isNull(); assertThat(day.getEndsAt()).isNull(); });
        assertThat(hours.findAllByStaffMemberId(staffId)).hasSize(1);
        assertThat(members.getWorkingHours(member(businessId))).hasSize(7);
    }
    @Test void updatesInPlaceAndCompletesMissingDaysForInactiveStaff() {
        var old = hours.saveAndFlush(StaffWorkingHours.builder().staffMemberId(staffId).dayOfWeek(DayOfWeek.MONDAY)
                .isAvailable(true).startsAt(java.time.LocalTime.of(9, 0)).endsAt(java.time.LocalTime.of(17, 0)).build());
        var request = new java.util.ArrayList<>(week(true, 10, 16));
        java.util.Collections.reverse(request);
        assertThat(members.replaceWorkingHours(staffId, request)).extracting("dayOfWeek").containsExactly(DayOfWeek.values());
        assertThat(hours.findById(old.getId()).orElseThrow().getStartsAt()).isEqualTo(java.time.LocalTime.of(10, 0));
        var ids = hours.findAllByStaffMemberIdOrderByDayOfWeekAsc(staffId).stream().map(StaffWorkingHours::getId).toList();
        members.replaceWorkingHours(staffId, week(false, 8, 20));
        assertThat(hours.findAllByStaffMemberIdOrderByDayOfWeekAsc(staffId)).extracting(StaffWorkingHours::getId).containsExactlyElementsOf(ids);
        assertThat(members.getWorkingHours(staffId)).allSatisfy(day -> { assertThat(day.getStartsAt()).isNull(); assertThat(day.getEndsAt()).isNull(); });
    }
    @Test void invalidWeekDoesNotChangePersistedHoursAndForeignStaffCannotBeReadOrWritten() {
        members.replaceWorkingHours(staffId, week(true, 9, 17));
        var request = week(true, 10, 16); request.get(3).setStartsAt(null);
        assertThatThrownBy(() -> members.replaceWorkingHours(staffId, request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(members.getWorkingHours(staffId)).allSatisfy(day -> assertThat(day.getStartsAt()).isEqualTo(java.time.LocalTime.of(9, 0)));
        var foreign = member(business());
        assertThatThrownBy(() -> members.getWorkingHours(foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> members.replaceWorkingHours(foreign, week(true, 9, 17))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void rollsBackExistingUpdatesWhenInsertingMissingDaysFails() {
        var old = hours.saveAndFlush(StaffWorkingHours.builder().staffMemberId(staffId).dayOfWeek(DayOfWeek.MONDAY)
                .isAvailable(true).startsAt(java.time.LocalTime.of(9, 0)).endsAt(java.time.LocalTime.of(17, 0)).build());
        doAnswer(invocation -> { hours.flush(); throw new IllegalStateException("insert failed"); }).when(hours).saveAll(anyList());
        assertThatThrownBy(() -> members.replaceWorkingHours(staffId, week(true, 10, 16))).hasMessage("insert failed");
        assertThat(hours.findAllByStaffMemberId(staffId)).hasSize(1);
        assertThat(hours.findById(old.getId()).orElseThrow().getStartsAt()).isEqualTo(java.time.LocalTime.of(9, 0));
    }
    @Test void concurrentReplacementsCannotMixDays() throws Exception {
        members.replaceWorkingHours(staffId, week(true, 9, 17));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); members.replaceWorkingHours(staffId, week(true, 10, 16)); return true; });
            var two = executor.submit(() -> { start.await(); members.replaceWorkingHours(staffId, week(true, 11, 15)); return true; });
            start.countDown(); one.get(15, TimeUnit.SECONDS); two.get(15, TimeUnit.SECONDS);
            var result = members.getWorkingHours(staffId);
            assertThat(result.stream().map(day -> day.getStartsAt()).distinct().toList()).hasSize(1);
            assertThat(result.getFirst().getStartsAt()).isIn(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 0));
        }
    }
    @Test void futureAvailabilityChangesWhileExistingAppointmentsRemainIntact() {
        var now = LocalDateTime.now().withNano(0);
        var date = java.time.LocalDate.now().plusDays(3);
        businessHours.saveAll(java.util.Arrays.stream(DayOfWeek.values()).map(day -> BusinessHours.builder().businessId(businessId).dayOfWeek(day)
                .opensAt(java.time.LocalTime.of(9, 0)).closesAt(java.time.LocalTime.of(18, 0)).isClosed(false).build()).toList());
        settings.saveAndFlush(BookingSettings.builder().businessId(businessId).bookingWindowDays(30).slotIntervalMinutes(30)
                .createdAt(now).updatedAt(now).build());
        var customer = customers.saveAndFlush(Customer.builder().businessId(businessId).name("Cliente").status(CustomerStatus.ACTIVE).createdAt(now).updatedAt(now).build());
        for (var status : List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED)) {
            var start = (status == AppointmentStatus.COMPLETED ? date.minusDays(5) : date).atTime(17, 0);
            appointments.saveAndFlush(Appointment.builder().businessId(businessId).customerId(customer.getId()).staffMemberId(staffId).serviceOfferingId(first)
                    .startsAt(start).endsAt(start.plusMinutes(30)).durationMinutes(30).priceCents(2000).status(status).source(AppointmentSource.ADMIN).createdAt(now).updatedAt(now).build());
        }
        var before = appointments.findAll().stream().filter(a -> a.getStaffMemberId().equals(staffId)).toList();
        members.replaceWorkingHours(staffId, week(true, 7, 21));
        var initial = availability.getAvailableSlots(date, date, first, staffId, null);
        assertThat(initial).isNotEmpty().allSatisfy(slot -> { assertThat(slot.getStartsAt().toLocalTime()).isAfterOrEqualTo(java.time.LocalTime.of(9, 0)); assertThat(slot.getEndsAt().toLocalTime()).isBeforeOrEqualTo(java.time.LocalTime.of(18, 0)); });
        members.replaceWorkingHours(staffId, week(true, 12, 15));
        assertThat(availability.getAvailableSlots(date, date, first, staffId, null)).isNotEmpty().allSatisfy(slot -> {
            assertThat(slot.getStartsAt().toLocalTime()).isAfterOrEqualTo(java.time.LocalTime.of(12, 0));
            assertThat(slot.getEndsAt().toLocalTime()).isBeforeOrEqualTo(java.time.LocalTime.of(15, 0));
        });
        members.replaceWorkingHours(staffId, week(false, 9, 17));
        assertThat(availability.getAvailableSlots(date, date, first, staffId, null)).isEmpty();
        assertThat(appointments.findAll().stream().filter(a -> a.getStaffMemberId().equals(staffId)).toList()).usingRecursiveComparison().isEqualTo(before);
    }
}

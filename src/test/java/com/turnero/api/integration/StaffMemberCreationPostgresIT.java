package com.turnero.api.integration;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.exception.StaffUserAlreadyLinkedException;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.*;
import com.turnero.api.repository.*;
import com.turnero.api.service.StaffMemberService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StaffMemberCreationPostgresIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
    @Autowired StaffMemberService service;
    @Autowired StaffMemberRepository staff;
    @Autowired BusinessRepository businesses;
    @Autowired UserRepository users;
    @Autowired BusinessHoursRepository hours;
    @MockitoSpyBean StaffWorkingHoursRepository workingHours;
    @MockitoBean CurrentBusinessContext context;
    private Long businessId;
    private Long userId;

    @BeforeEach
    void setUp() {
        var now = LocalDateTime.now();
        businessId = businesses.saveAndFlush(Business.builder().name("Studio").slug(UUID.randomUUID().toString())
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP).createdAt(now).updatedAt(now).build()).getId();
        userId = users.saveAndFlush(User.builder().businessId(businessId).name("Ana").email("ana@example.com")
                .authProvider(AuthProvider.GOOGLE).authSubject(UUID.randomUUID().toString()).role(UserRole.OWNER)
                .createdAt(now).updatedAt(now).build()).getId();
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean closed = day == DayOfWeek.SUNDAY;
            hours.save(BusinessHours.builder().businessId(businessId).dayOfWeek(day)
                    .opensAt(closed ? null : LocalTime.of(9, 0)).closesAt(closed ? null : LocalTime.of(18, 0))
                    .isClosed(closed).build());
        }
        when(context.getCurrentBusinessId()).thenReturn(businessId);
    }
    private StaffMember member(Long linkedUser) {
        return StaffMember.builder().name("Ana").roleLabel("x".repeat(80)).specialty("Colorista").userId(linkedUser).build();
    }
    @Test
    void createsWithAndWithoutLinkAndCopiesTheFullWeek() {
        for (Long linkedUser : new Long[]{null, userId}) {
            StaffMember saved = service.saveStaffMember(member(linkedUser));
            assertThat(saved.getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);
            assertThat(saved.getBusinessId()).isEqualTo(businessId);
            assertThat(saved.getUserId()).isEqualTo(linkedUser);
            var week = workingHours.findAllByStaffMemberIdOrderByDayOfWeekAsc(saved.getId());
            assertThat(week).hasSize(7);
            for (var day : week) {
                assertThat(day.isAvailable()).isEqualTo(day.getDayOfWeek() != DayOfWeek.SUNDAY);
                assertThat(day.getStartsAt()).isEqualTo(day.isAvailable() ? LocalTime.of(9, 0) : null);
                assertThat(day.getEndsAt()).isEqualTo(day.isAvailable() ? LocalTime.of(18, 0) : null);
            }
        }
    }
    @Test
    void rejectsLinkToAnInactiveProfessionalAndLeavesExistingDataIntact() {
        var saved = service.saveStaffMember(member(userId));
        saved.setStatus(StaffMemberStatus.INACTIVE);
        staff.saveAndFlush(saved);
        assertThatThrownBy(() -> service.saveStaffMember(member(userId))).isInstanceOf(StaffUserAlreadyLinkedException.class);
        assertThat(staff.findAllByBusinessId(businessId)).hasSize(1);
        assertThat(workingHours.findAllByStaffMemberIdOrderByDayOfWeekAsc(saved.getId())).hasSize(7);
    }
    @Test
    void rejectsAUserOutsideTheCurrentBusiness() {
        var another = businesses.saveAndFlush(Business.builder().name("Other").slug(UUID.randomUUID().toString())
                .timezone("UTC").status(BusinessStatus.ACTIVE).onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        when(context.getCurrentBusinessId()).thenReturn(another.getId());
        assertThatThrownBy(() -> service.saveStaffMember(member(userId))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(staff.findAllByBusinessId(another.getId())).isEmpty();
    }
    @Test
    void rollsBackTheProfessionalWhenCopyingHoursFails() {
        doThrow(new IllegalStateException("copy failed")).when(workingHours).saveAll(anyList());
        assertThatThrownBy(() -> service.saveStaffMember(member(userId))).hasMessage("copy failed");
        assertThat(staff.findAllByBusinessId(businessId)).isEmpty();
        assertThat(staff.existsByUserIdAndDeletedAtIsNull(userId)).isFalse();
    }
    @Test
    void serializesConcurrentLinksIntoOneSuccessAndOneConflict() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<String> create = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException();
                try { service.saveStaffMember(member(userId)); return "created"; }
                catch (StaffUserAlreadyLinkedException ex) { return "conflict"; }
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "conflict");
            assertThat(staff.findAllByBusinessId(businessId)).hasSize(1);
        }
    }
}

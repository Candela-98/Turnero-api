package com.turnero.api.integration;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BusinessHoursDayRequestDto;
import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.model.Business;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.BusinessHoursRepository;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.service.BusinessHoursService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BusinessHoursPostgresIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero_test")
            .withUsername("turnero")
            .withPassword("turnero");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private BusinessHoursService businessHoursService;
    @Autowired private BusinessHoursRepository businessHoursRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoBean private CurrentBusinessContext currentBusinessContext;

    @Test
    void replacingAnExistingWeekUpdatesOnlyMondayAndPreservesIdsAndOtherBusinesses() {
        Long businessId = businessRepository.save(business("update-one")).getId();
        Long otherBusinessId = businessRepository.save(business("update-other")).getId();
        businessHoursRepository.saveAll(week(businessId, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        businessHoursRepository.saveAll(week(otherBusinessId, LocalTime.of(11, 0), LocalTime.of(17, 0)));
        Map<DayOfWeek, BusinessHours> before = byDay(businessHoursRepository.findAllByBusinessId(businessId));
        Map<DayOfWeek, BusinessHours> otherBefore = byDay(businessHoursRepository.findAllByBusinessId(otherBusinessId));
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(businessId);

        BusinessHoursReplaceRequestDto request = request(LocalTime.of(9, 0), LocalTime.of(18, 0));
        request.getHours().getFirst().setClosesAt(LocalTime.of(13, 0));
        Statistics statistics = statistics();
        statistics.clear();

        List<BusinessHours> result = businessHoursService.replaceCurrentBusinessHours(request);

        assertThat(result).extracting(BusinessHours::getDayOfWeek).containsExactly(DayOfWeek.values());
        assertThat(statistics.getEntityStatistics(BusinessHours.class.getName()).getUpdateCount()).isEqualTo(1);
        assertThat(statistics.getEntityStatistics(BusinessHours.class.getName()).getInsertCount()).isZero();
        assertThat(statistics.getEntityStatistics(BusinessHours.class.getName()).getDeleteCount()).isZero();
        Map<DayOfWeek, BusinessHours> after = byDay(businessHoursRepository.findAllByBusinessId(businessId));
        assertThat(after).hasSize(7);
        assertThat(after.get(DayOfWeek.MONDAY).getClosesAt()).isEqualTo(LocalTime.of(13, 0));
        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(after.get(day).getId()).isEqualTo(before.get(day).getId());
            if (day != DayOfWeek.MONDAY) {
                assertThat(after.get(day).getOpensAt()).isEqualTo(before.get(day).getOpensAt());
                assertThat(after.get(day).getClosesAt()).isEqualTo(before.get(day).getClosesAt());
            }
        }
        Map<DayOfWeek, BusinessHours> otherAfter = byDay(businessHoursRepository.findAllByBusinessId(otherBusinessId));
        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(otherAfter.get(day).getId()).isEqualTo(otherBefore.get(day).getId());
            assertThat(otherAfter.get(day).getOpensAt()).isEqualTo(otherBefore.get(day).getOpensAt());
            assertThat(otherAfter.get(day).getClosesAt()).isEqualTo(otherBefore.get(day).getClosesAt());
        }

        statistics.clear();
        businessHoursService.replaceCurrentBusinessHours(request);
        assertThat(statistics.getEntityStatistics(BusinessHours.class.getName()).getUpdateCount()).isZero();
        assertThat(statistics.getEntityStatistics(BusinessHours.class.getName()).getInsertCount()).isZero();
        assertThat(statistics.getEntityStatistics(BusinessHours.class.getName()).getDeleteCount()).isZero();
    }

    @Test
    void replacingAnEmptyWeekCreatesAllSevenDays() {
        Long businessId = businessRepository.save(business("initialize")).getId();
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(businessId);

        List<BusinessHours> result = businessHoursService.replaceCurrentBusinessHours(
                request(LocalTime.of(9, 0), LocalTime.of(18, 0)));

        assertThat(result).hasSize(7);
        assertThat(result).extracting(BusinessHours::getDayOfWeek).containsExactly(DayOfWeek.values());
        assertThat(result).allMatch(hour -> hour.getId() != null);
        assertThat(businessHoursRepository.findAllByBusinessId(businessId)).hasSize(7);
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private Map<DayOfWeek, BusinessHours> byDay(List<BusinessHours> hours) {
        return hours.stream().collect(Collectors.toMap(BusinessHours::getDayOfWeek, Function.identity()));
    }

    private Business business(String slug) {
        return Business.builder()
                .name("Business " + slug)
                .slug(slug)
                .timezone("America/Argentina/Buenos_Aires")
                .status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private List<BusinessHours> week(Long businessId, LocalTime opensAt, LocalTime closesAt) {
        return List.of(DayOfWeek.values()).stream().map(day -> BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(day)
                .opensAt(day == DayOfWeek.SUNDAY ? null : opensAt)
                .closesAt(day == DayOfWeek.SUNDAY ? null : closesAt)
                .isClosed(day == DayOfWeek.SUNDAY)
                .build()).toList();
    }

    private BusinessHoursReplaceRequestDto request(LocalTime opensAt, LocalTime closesAt) {
        List<BusinessHoursDayRequestDto> hours = List.of(DayOfWeek.values()).stream().map(day -> {
            BusinessHoursDayRequestDto hour = new BusinessHoursDayRequestDto();
            hour.setDayOfWeek(day);
            hour.setIsClosed(day == DayOfWeek.SUNDAY);
            hour.setOpensAt(day == DayOfWeek.SUNDAY ? null : opensAt);
            hour.setClosesAt(day == DayOfWeek.SUNDAY ? null : closesAt);
            return hour;
        }).toList();
        BusinessHoursReplaceRequestDto request = new BusinessHoursReplaceRequestDto();
        request.setHours(hours);
        return request;
    }
}

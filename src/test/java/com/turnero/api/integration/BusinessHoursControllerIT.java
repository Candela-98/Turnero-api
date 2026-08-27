package com.turnero.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.BusinessHoursDayRequestDto;
import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.model.Business;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.BusinessHoursRepository;
import com.turnero.api.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BusinessHoursControllerIT {
    private static final String BASE_URL = "/api/v1/business-hours";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessHoursRepository businessHoursRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        businessHoursRepository.deleteAll();
        businessRepository.deleteAll();
        businessRepository.flush();
        jdbcTemplate.execute("ALTER TABLE businesses ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.execute("ALTER TABLE business_hours ALTER COLUMN id RESTART WITH 1");
        businessRepository.saveAllAndFlush(List.of(business("one"), business("two")));
    }

    @Test
    void getBusinessHours_returnsOnlyTheCurrentBusinessWeek() throws Exception {
        businessHoursRepository.saveAll(fullWeekEntities(1L, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        businessHoursRepository.saveAll(fullWeekEntities(2L, LocalTime.of(11, 0), LocalTime.of(12, 0)));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data[0].opens_at").value("09:00"));
    }

    @Test
    void replaceBusinessHours_persistsTheFullWeekAndLeavesOtherBusinessesUntouched() throws Exception {
        businessHoursRepository.saveAll(fullWeekEntities(1L, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        businessHoursRepository.saveAll(fullWeekEntities(2L, LocalTime.of(11, 0), LocalTime.of(12, 0)));

        mockMvc.perform(put(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullWeekRequest(LocalTime.of(10, 0), LocalTime.of(16, 0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].opens_at").value("10:00"));

        List<BusinessHours> currentBusinessHours = businessHoursRepository.findAllByBusinessId(1L);
        List<BusinessHours> otherBusinessHours = businessHoursRepository.findAllByBusinessId(2L);
        assertThat(currentBusinessHours).hasSize(7);
        assertThat(currentBusinessHours).allMatch(hour -> hour.getOpensAt().equals(LocalTime.of(10, 0)));
        assertThat(otherBusinessHours).hasSize(7);
        assertThat(otherBusinessHours).allMatch(hour -> hour.getOpensAt().equals(LocalTime.of(11, 0)));
    }

    @Test
    void replaceBusinessHours_whenRangeIsInvalid_keepsTheExistingWeek() throws Exception {
        businessHoursRepository.saveAll(fullWeekEntities(1L, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        BusinessHoursReplaceRequestDto request = fullWeekRequest(LocalTime.of(10, 0), LocalTime.of(16, 0));
        request.getHours().getFirst().setOpensAt(LocalTime.of(16, 0));

        mockMvc.perform(put(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(businessHoursRepository.findAllByBusinessId(1L))
                .hasSize(7)
                .allMatch(hour -> hour.getOpensAt().equals(LocalTime.of(9, 0)));
    }

    private Business business(String suffix) {
        return Business.builder().name("Business " + suffix).slug("business-" + suffix)
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
    }

    private List<BusinessHours> fullWeekEntities(long businessId, LocalTime opensAt, LocalTime closesAt) {
        return List.of(DayOfWeek.values()).stream()
                .map(day -> BusinessHours.builder().businessId(businessId).dayOfWeek(day)
                        .opensAt(opensAt).closesAt(closesAt).isClosed(false).build())
                .toList();
    }

    private BusinessHoursReplaceRequestDto fullWeekRequest(LocalTime opensAt, LocalTime closesAt) {
        List<BusinessHoursDayRequestDto> hours = List.of(DayOfWeek.values()).stream().map(day -> {
            BusinessHoursDayRequestDto hour = new BusinessHoursDayRequestDto();
            hour.setDayOfWeek(day);
            hour.setOpensAt(opensAt);
            hour.setClosesAt(closesAt);
            hour.setIsClosed(false);
            return hour;
        }).toList();
        BusinessHoursReplaceRequestDto request = new BusinessHoursReplaceRequestDto();
        request.setHours(hours);
        return request;
    }
}

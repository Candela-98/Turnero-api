package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.BusinessHoursDayRequestDto;
import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.mapper.BusinessHoursMapper;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.service.BusinessHoursService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessHoursController.class)
class BusinessHoursControllerTest {
    private static final String BASE_URL = "/api/v1/business-hours";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private BusinessHoursService businessHoursService;
    @MockitoBean private BusinessHoursMapper businessHoursMapper;

    @Test
    void getBusinessHours_returnsContractWrapper() throws Exception {
        BusinessHours monday = BusinessHours.builder().id(1L).dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0)).closesAt(LocalTime.of(18, 0)).isClosed(false).build();
        given(businessHoursService.getCurrentBusinessHours()).willReturn(List.of(monday));
        given(businessHoursMapper.toResponseDtoList(List.of(monday))).willReturn(List.of(
                com.turnero.api.dto.BusinessHoursResponseDto.builder().id(1L).dayOfWeek(DayOfWeek.MONDAY)
                        .opensAt(LocalTime.of(9, 0)).closesAt(LocalTime.of(18, 0)).isClosed(false).build()
        ));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data[0].opens_at").value("09:00"))
                .andExpect(jsonPath("$.data[0].is_closed").value(false));
    }

    @Test
    void replaceBusinessHours_whenWeekIsIncomplete_returnsValidationError() throws Exception {
        BusinessHoursReplaceRequestDto request = new BusinessHoursReplaceRequestDto();
        request.setHours(List.of(day(DayOfWeek.MONDAY)));

        mockMvc.perform(put(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("hours"));

        then(businessHoursService).shouldHaveNoInteractions();
    }

    @Test
    void replaceBusinessHours_delegatesValidWeek() throws Exception {
        BusinessHoursReplaceRequestDto request = new BusinessHoursReplaceRequestDto();
        request.setHours(List.of(DayOfWeek.values()).stream().map(this::day).toList());
        given(businessHoursService.replaceCurrentBusinessHours(any())).willReturn(List.of());
        given(businessHoursMapper.toResponseDtoList(List.of())).willReturn(List.of());

        mockMvc.perform(put(BASE_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        then(businessHoursService).should().replaceCurrentBusinessHours(any());
    }

    private BusinessHoursDayRequestDto day(DayOfWeek day) {
        BusinessHoursDayRequestDto request = new BusinessHoursDayRequestDto();
        request.setDayOfWeek(day);
        request.setIsClosed(false);
        request.setOpensAt(LocalTime.of(9, 0));
        request.setClosesAt(LocalTime.of(18, 0));
        return request;
    }
}

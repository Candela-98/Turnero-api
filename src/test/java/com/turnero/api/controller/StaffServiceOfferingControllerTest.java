package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.StaffServiceOfferingRequestDto;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.service.StaffServiceOfferingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffServiceOfferingController.class)
public class StaffServiceOfferingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffServiceOfferingService staffServiceOfferingService;
    @MockitoBean
    private AdminAuthInterceptor adminAuthInterceptor;

    private static final String BASE_URL = "/api/v1/staff-members";

    @BeforeEach
    void allowAdminRequests() throws Exception {
        given(adminAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
    }

    private ServOfferingResponseDto getServiceOfferingResponseDto(Long id) {
        return ServOfferingResponseDto.builder()
                .id(id)
                .name("Haircut")
                .category("Hair")
                .durationMinutes(30)
                .priceCents(2500)
                .status(ServiceOfferingStatus.ACTIVE)
                .build();
    }

    @Test
    void getServiceOfferings_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        Long staffMemberId = 1L;

        given(staffServiceOfferingService.getServiceOfferings(staffMemberId))
                .willReturn(List.of(
                        getServiceOfferingResponseDto(10L),
                        getServiceOfferingResponseDto(20L)
                ));

        // When + Then
        mockMvc.perform(get(BASE_URL + "/1/service-offerings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[1].id").value(20));

        then(staffServiceOfferingService)
                .should()
                .getServiceOfferings(staffMemberId);
    }

    @Test
    void replaceServiceOfferings_ok_shouldReturn204_andCallService() throws Exception {
        Long staffMemberId = 1L;

        StaffServiceOfferingRequestDto request = StaffServiceOfferingRequestDto.builder()
                .serviceOfferingIds(List.of(10L, 20L))
                .build();

        mockMvc.perform(put(BASE_URL + "/1/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        then(staffServiceOfferingService)
                .should()
                .replaceServiceOfferings(staffMemberId, List.of(10L, 20L));
    }
}


package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.dto.BusinessResponseDto;
import com.turnero.api.dto.BusinessUpdateRequestDto;
import com.turnero.api.mapper.BusinessMapper;
import com.turnero.api.model.Business;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.service.BusinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessController.class)
class BusinessControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private BusinessService businessService;
    @MockitoBean private BusinessMapper businessMapper;
    @MockitoBean private AdminAuthInterceptor adminAuthInterceptor;

    @BeforeEach
    void allowAdminRequests() throws Exception {
        given(adminAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
    }

    @Test
    void getBusiness_returnsCurrentBusiness() throws Exception {
        Business business = Business.builder().id(1L).build();
        given(businessService.getCurrentBusiness()).willReturn(business);
        given(businessMapper.toResponseDto(business)).willReturn(response());

        mockMvc.perform(get("/api/v1/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("barber-studio"))
                .andExpect(jsonPath("$.onboarding_status").value("PENDING_SETUP"));
    }

    @Test
    void patchBusiness_updatesAllowedFields() throws Exception {
        BusinessUpdateRequestDto request = new BusinessUpdateRequestDto();
        request.setName("Barber Studio Palermo");
        Business business = Business.builder().id(1L).build();
        given(businessService.updateCurrentBusiness(any())).willReturn(business);
        given(businessMapper.toResponseDto(business)).willReturn(response());

        mockMvc.perform(patch("/api/v1/business").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Barber Studio"));
    }

    private BusinessResponseDto response() {
        return BusinessResponseDto.builder().id(1L).name("Barber Studio").slug("barber-studio")
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP).build();
    }
}

package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.dto.BookingSettingsResponseDto;
import com.turnero.api.dto.BookingSettingsUpdateRequestDto;
import com.turnero.api.mapper.BookingSettingsMapper;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.service.BookingSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingSettingsController.class)
class BookingSettingsControllerTest {
    private static final String BASE_URL = "/api/v1/booking-settings";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private BookingSettingsService bookingSettingsService;
    @MockitoBean private BookingSettingsMapper bookingSettingsMapper;
    @MockitoBean private AdminAuthInterceptor adminAuthInterceptor;

    @BeforeEach
    void allowAdminRequests() throws Exception {
        given(adminAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
    }

    @Test
    void getBookingSettings_returnsCurrentBusinessSettings() throws Exception {
        BookingSettings settings = BookingSettings.builder().businessId(1L).slotIntervalMinutes(30).build();
        BookingSettingsResponseDto response = BookingSettingsResponseDto.builder()
                .bookingWindowDays(7).slotIntervalMinutes(30).build();
        given(bookingSettingsService.getCurrentBookingSettings()).willReturn(settings);
        given(bookingSettingsMapper.toResponseDto(settings)).willReturn(response);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking_window_days").value(7))
                .andExpect(jsonPath("$.slot_interval_minutes").value(30));

        then(bookingSettingsService).should().getCurrentBookingSettings();
    }

    @Test
    void updateBookingSettings_whenRequestIsValid_returnsUpdatedSettings() throws Exception {
        BookingSettings settings = BookingSettings.builder().businessId(1L).slotIntervalMinutes(15).build();
        BookingSettingsResponseDto response = BookingSettingsResponseDto.builder()
                .publicBookingEnabled(true).slotIntervalMinutes(15).build();
        given(bookingSettingsService.updateCurrentBookingSettings(any(BookingSettingsUpdateRequestDto.class)))
                .willReturn(settings);
        given(bookingSettingsMapper.toResponseDto(settings)).willReturn(response);

        mockMvc.perform(patch(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"public_booking_enabled\":true,\"slot_interval_minutes\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public_booking_enabled").value(true))
                .andExpect(jsonPath("$.slot_interval_minutes").value(15));

        then(bookingSettingsService).should().updateCurrentBookingSettings(any(BookingSettingsUpdateRequestDto.class));
    }

    @Test
    void updateBookingSettings_whenCustomerLoginIsRequired_returnsValidationError() throws Exception {
        mockMvc.perform(patch(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requires_customer_login\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("requiresCustomerLogin"));

        then(bookingSettingsService).shouldHaveNoInteractions();
    }
}

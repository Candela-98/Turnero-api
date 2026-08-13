package com.turnero.api.controller;

import com.turnero.api.dto.AvailabilitySlotResponseDto;
import com.turnero.api.service.AvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AvailabilityController.class)
public class AvailabilityControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    private static final String BASE_URL = "/api/v1/availability/slots";

    @Test
    void getAvailableSlots_withDate_shouldReturn200AndList() throws Exception {
        // Given
        LocalDate date = LocalDate.of(2024, 6, 10);
        Long serviceOfferingId = 1L;
        Long staffMemberId = 2L;

        AvailabilitySlotResponseDto slot = AvailabilitySlotResponseDto.builder()
                .startsAt(LocalDateTime.of(2024, 6, 10, 9, 0))
                .endsAt(LocalDateTime.of(2024, 6, 10, 9, 30))
                .available(true)
                .build();

        given(availabilityService.getAvailableSlots(date, date, serviceOfferingId, staffMemberId, null)).willReturn(List.of(slot));

        // When + Then
        mockMvc.perform(get(BASE_URL).param("date", "2024-06-10")
                        .param("service_offering_id", "1")
                        .param("staff_member_id", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startsAt").value("2024-06-10T09:00:00"))
                .andExpect(jsonPath("$[0].endsAt").value("2024-06-10T09:30:00"))
                .andExpect(jsonPath("$[0].available").value(true));

        then(availabilityService).should().getAvailableSlots(date, date, serviceOfferingId, staffMemberId, null);
    }

    @Test
    void getAvailableSlots_withFromAndTo_shouldReturn200AndList() throws Exception {
        // Given
        LocalDate from = LocalDate.of(2024, 6, 10);
        LocalDate to = LocalDate.of(2024, 6, 11);
        Long serviceOfferingId = 1L;
        Long staffMemberId = 2L;
        Long excludeAppointmentId = 10L;

        AvailabilitySlotResponseDto slot = AvailabilitySlotResponseDto.builder()
                .startsAt(LocalDateTime.of(2024, 6, 10, 9, 0))
                .endsAt(LocalDateTime.of(2024, 6, 10, 9, 30))
                .available(true)
                .build();

        given(availabilityService.getAvailableSlots(
                from,
                to,
                serviceOfferingId,
                staffMemberId,
                excludeAppointmentId
        )).willReturn(List.of(slot));

        // When + Then
        mockMvc.perform(get(BASE_URL)
                        .param("from", "2024-06-10")
                        .param("to", "2024-06-11")
                        .param("service_offering_id", "1")
                        .param("staff_member_id", "2")
                        .param("exclude_appointment_id", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startsAt").value("2024-06-10T09:00:00"))
                .andExpect(jsonPath("$[0].endsAt").value("2024-06-10T09:30:00"))
                .andExpect(jsonPath("$[0].available").value(true));

        then(availabilityService).should().getAvailableSlots(from, to, serviceOfferingId, staffMemberId, excludeAppointmentId);
    }

    @Test
    void getAvailableSlots_withDateAndRange_shouldReturn400() throws Exception {
        // When + Then
        mockMvc.perform(get(BASE_URL)
                        .param("date", "2024-06-10")
                        .param("from", "2024-06-10")
                        .param("to", "2024-06-11")
                        .param("service_offering_id", "1")
                        .param("staff_member_id", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("date cannot be combined with from or to"))
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(availabilityService).shouldHaveNoInteractions();
    }

    @Test
    void getAvailableSlots_withoutDateOrRange_shouldReturn400() throws Exception {
        // When + Then
        mockMvc.perform(get(BASE_URL)
                        .param("service_offering_id", "1")
                        .param("staff_member_id", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Provide date or both from and to"))
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(availabilityService).shouldHaveNoInteractions();
    }
}

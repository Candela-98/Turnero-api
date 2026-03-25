package com.turnero.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.AppointmentStatus;
import com.turnero.api.model.Appointment;
import com.turnero.api.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AppointmentController.class)
public class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private AppointmentMapper appointmentMapper;

    private AppointmentRequestDto validDto(Long id) {
        AppointmentRequestDto dto = new AppointmentRequestDto();
        dto.setCustomerId(1L);
        dto.setServiceId(2L);
        dto.setStaffMemberId(3L);
        dto.setDateTime(LocalDateTime.now().plusDays(1)); // cumple @Future
        dto.setDurationMinutes(30); // cumple @Min(1)
        dto.setStatus(AppointmentStatus.CONFIRMED);
        dto.setNotes("Notes");
        return dto;
    }

    private Appointment getAppointmentEntity(Long id) {
        Appointment t = new Appointment();
        t.setCustomerId(id);
        t.setServiceId(2L);
        t.setStaffMemberId(3L);
        t.setDateTime(LocalDateTime.of(2026, 2, 15, 10, 0));
        t.setDurationMinutes(30);
        t.setStatus(AppointmentStatus.CONFIRMED);
        t.setNotes("Notes");
        t.setCreatedAt(LocalDateTime.of(2026, 2, 1, 10, 0));
        t.setUpdateAt(LocalDateTime.of(2026, 2, 2, 10, 0));
        return t;
    }

    private Appointment appointmentWhitId(long id) {
        Appointment t = new Appointment();
        t.setId(id);
        t.setCustomerId(1L);
        t.setServiceId(2L);
        t.setStaffMemberId(3L);
        t.setDateTime(LocalDateTime.of(2026, 2, 15, 10, 0));
        t.setDurationMinutes(30);
        t.setStatus(AppointmentStatus.CONFIRMED);
        t.setNotes("Notes");
        t.setCreatedAt(LocalDateTime.of(2026, 2, 1, 10, 0));
        t.setUpdateAt(LocalDateTime.of(2026, 2, 2, 10, 0));
        return t;
    }

    @Test
    void saveAppointment_ok_shouldReturn200_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var dto = validDto(id);
        Appointment entity = new Appointment();

        given(appointmentMapper.toEntity(any(AppointmentRequestDto.class)))
                .willReturn(entity);

        // When
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // Assert
        then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
        then(appointmentService).should().saveAppointment(entity);
    }

    @Test
    void saveAppointment_withInvalidDto_shouldReturn400() throws Exception{
        // Given
        AppointmentRequestDto dto = validDto(1L);
        dto.setCustomerId(null);

        // When + Then
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        then(appointmentService).shouldHaveNoInteractions();
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void findAllAppointment() throws Exception{
        //Given
        given(appointmentService.findAllAppointments()).willReturn(List.of(
                appointmentWhitId(1),
                appointmentWhitId(2)
        ));

        //When + Then
        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        then(appointmentService).should().findAllAppointments();
    }

    @Test
    void findAppointment() throws Exception {
        //Given
        given(appointmentService.findAppointment(10L)).willReturn(appointmentWhitId(10));

        //When + Then
        mockMvc.perform(get("/api/appointments/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        then(appointmentService).should().findAppointment(10L);
    }

    @Test
    void updateAppointment_ok_shouldReturn200_andCallService() throws Exception{
        //Given
        AppointmentRequestDto dto = validDto(5L);
        Appointment entity = new Appointment();

        given(appointmentMapper.toEntity(any(AppointmentRequestDto.class))).willReturn(entity);

        //When + Then
            mockMvc.perform(put("/api/appointments/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
            then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
            then(appointmentService).should().updateAppointment(entity, 5L);

    }

    @Test
    void updateAppointment_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        AppointmentRequestDto dto = validDto(5L);
        dto.setDateTime(LocalDateTime.now().minusDays(1)); //rompe @Feature

        //When + Then
        mockMvc.perform(put("/api/appointments/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
        then(appointmentService).shouldHaveNoInteractions();
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void deleteAppointment_ok_shouldReturn200_andCallService() throws Exception{
        //Given
        Long id = 7L;

        //When + Then
        mockMvc.perform(delete("/api/appointments/7"))
                .andExpect(status().isNoContent());
        then(appointmentService).should().deleteAppointment(7L);
    }

}

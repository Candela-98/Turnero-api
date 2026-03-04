package com.turnero.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.AppointmentRequestDto;
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

    private AppointmentRequestDto validDto() {
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
        AppointmentRequestDto dto = validDto();
        Appointment entity = new Appointment();

        when(appointmentMapper.toEntity(any(AppointmentRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(appointmentMapper).toEntity(any(AppointmentRequestDto.class));
        verify(appointmentService).saveAppointment(entity);
    }

    @Test
    void saveAppointment_withInvalidDto_shouldReturn400() throws Exception{
        AppointmentRequestDto dto = validDto();
        dto.setCustomerId(null); // invalida por @NotNull

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(appointmentService, never()).saveAppointment(any());
        verify(appointmentMapper, never()).toEntity(any());
    }

    @Test
    void findAllAppointment() throws Exception{
        when(appointmentService.findAllAppointments()).thenReturn(List.of(appointmentWhitId(1L), appointmentWhitId(2L)));

        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(appointmentService).findAllAppointments();

    }

    @Test
    void findAppointment() throws Exception {
        when(appointmentService.findAppointment(10L)).thenReturn(appointmentWhitId(10));

        mockMvc.perform(get("/api/appointments/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(appointmentService).findAppointment(10L);
    }

    @Test
    void updateAppointment_ok_shouldReturn200_andCallService() throws Exception{
        AppointmentRequestDto dto = validDto();
        Appointment entity = new Appointment();

        when(appointmentMapper.toEntity(any(AppointmentRequestDto.class))).thenReturn(entity);
            mockMvc.perform(put("/api/appointments/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
            verify(appointmentMapper).toEntity(any(AppointmentRequestDto.class));
            verify(appointmentService).updateAppointment(entity, 5L);

    }

    @Test
    void updateAppointment_withInvalidDto_shouldReturn400() throws Exception{
        AppointmentRequestDto dto = validDto();
        dto.setDateTime(LocalDateTime.now().minusDays(1)); //rompe @Feature

        mockMvc.perform(put("/api/appointments/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
        verify(appointmentService, never()).updateAppointment(any(), anyLong());
        verify(appointmentMapper, never()).toEntity(any());
    }

    @Test
    void deleteAppointment_ok_shouldReturn200_andCallService() throws Exception{
        mockMvc.perform(delete("/api/appointments/7"))
                .andExpect(status().isOk());
        verify(appointmentService).deleteAppointment(7L);
    }

}

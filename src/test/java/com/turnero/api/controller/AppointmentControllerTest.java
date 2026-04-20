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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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

    private AppointmentRequestDto getAppointmentDto(Long id) {
        return AppointmentRequestDto.builder()
                .customerId(id)
                .serviceId(2L)
                .staffMemberId(3L)
                .dateTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .notes("Notes")
                .build();
    }

    private Appointment getAppointmentEntity(Long id) {
        return  Appointment.builder()
                .id(id)
                .customerId(id)
                .serviceId(2L)
                .staffMemberId(3L)
                .dateTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .notes("Notes")
                .createdAt(LocalDateTime.now())
                .updateAt(LocalDateTime.now())
                .build();
    }

    @Test
    void saveAppointment_ok_shouldReturn201_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var dto = getAppointmentDto(id);
        Appointment entity = getAppointmentEntity(id);

        given(appointmentMapper.toEntity(any(AppointmentRequestDto.class)))
                .willReturn(entity);

        // When
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Assert
        then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
        then(appointmentService).should().saveAppointment(entity);
    }

    @Test
    void saveAppointment_withInvalidDto_shouldReturn400() throws Exception{
        // Given
        Long id = 1L;
        AppointmentRequestDto dto = getAppointmentDto(id);
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
    void findAllAppointment_shouldReturn200_andList() throws Exception{
        //Given
        Long id = 1L;
        var appointment1 = getAppointmentEntity(id);
        var appointment2 = getAppointmentEntity(2L);
        given(appointmentService.findAllAppointments()).willReturn(List.of(appointment1, appointment2));

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
    void findAppointment_ok_shouldReturn200_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var appointment = getAppointmentEntity(id);
        given(appointmentService.findAppointment(id)).willReturn(appointment);

        //When + Then
        mockMvc.perform(get("/api/appointments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        then(appointmentService).should().findAppointment(id);
    }

    @Test
    void updateAppointment_ok_shouldReturn200_andCallService() throws Exception{
        //Given
        Long id = 5L;
        var dto = getAppointmentDto(id);
        var entity = getAppointmentEntity(id);

        given(appointmentMapper.toEntity(any(AppointmentRequestDto.class))).willReturn(entity);

        //When + Then
            mockMvc.perform(put("/api/appointments/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNoContent());
            then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
            then(appointmentService).should().updateAppointment(entity,id);

    }

    @Test
    void updateAppointment_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        Long id = 5L;
        var dto = getAppointmentDto(id);
        dto.setDateTime(LocalDateTime.now().minusDays(1));

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

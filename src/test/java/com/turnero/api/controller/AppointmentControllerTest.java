package com.turnero.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.enums.AppointmentStatus;
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
import static org.mockito.BDDMockito.*;
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
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .customerNotes("Notes")
                .build();
    }

    private Appointment getAppointmentEntity(Long id) {
        return  Appointment.builder()
                .id(id)
                .customerId(id)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .customerNotes("Notes")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private AppointmentResponseDto getAppointmentResponseDto(Long id) {
        return AppointmentResponseDto.builder()
                .id(id)
                .customerId(id)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .customerNotes("Notes")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void saveAppointment_ok_shouldReturn201_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var dto = getAppointmentDto(id);
        var responseDto = getAppointmentResponseDto(id);
        Appointment entity = getAppointmentEntity(id);

        given(appointmentMapper.toEntity(any(AppointmentRequestDto.class)))
                .willReturn(entity);
        given(appointmentMapper.toResponseDto(entity)).willReturn(responseDto);

        // When
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.serviceOfferingId").value(2))
                .andExpect(jsonPath("$.staffMemberId").value(3))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Assert
        then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
        then(appointmentService).should().saveAppointment(entity);
        then(appointmentMapper).should().toResponseDto(entity);
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
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.customerId").exists());

        then(appointmentService).shouldHaveNoInteractions();
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void saveAppointment_whenCustomerDoesNotExist_shouldReturn404() throws Exception {
        // Given
        Long id = 1L;
        AppointmentRequestDto dto = getAppointmentDto(id);
        Appointment entity = getAppointmentEntity(id);

        given(appointmentMapper.toEntity(any(AppointmentRequestDto.class)))
                .willReturn(entity);
        willThrow(new ResourceNotFoundException("Customer not found."))
                .given(appointmentService)
                .saveAppointment(entity);

        // When + Then
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."));

        then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
        then(appointmentService).should().saveAppointment(entity);
    }

    @Test
    void findAllAppointment_shouldReturn200_andList() throws Exception{
        //Given
        Long id = 1L;
        var appointment1 = getAppointmentEntity(id);
        var appointment2 = getAppointmentEntity(2L);

        var responseDto1 = getAppointmentResponseDto(id);
        var responseDto2 = getAppointmentResponseDto(2L);

        given(appointmentService.findAllAppointments()).willReturn(List.of(appointment1, appointment2));
        given(appointmentMapper.toResponseDtoList(List.of(appointment1, appointment2)))
                .willReturn(List.of(responseDto1, responseDto2));

        //When + Then
        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        then(appointmentService).should().findAllAppointments();
        then(appointmentMapper).should().toResponseDtoList(List.of(appointment1, appointment2));
    }

    @Test
    void findAppointment_ok_shouldReturn200_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var appointment = getAppointmentEntity(id);
        var responseDto = getAppointmentResponseDto(id);

        given(appointmentService.findAppointment(id)).willReturn(appointment);
        given(appointmentMapper.toResponseDto(appointment)).willReturn(responseDto);

        //When + Then
        mockMvc.perform(get("/api/appointments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.serviceOfferingId").value(2))
                .andExpect(jsonPath("$.staffMemberId").value(3))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        then(appointmentService).should().findAppointment(id);
        then(appointmentMapper).should().toResponseDto(appointment);
    }

    @Test
    void findAppointment_withNonExistingId_shouldReturn404() throws Exception {
        //Given
        Long id = 999L;
        given(appointmentService.findAppointment(id))
                .willThrow(new ResourceNotFoundException("Appointment not found"));

        //When + Then
        mockMvc.perform(get("/api/appointments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"));

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
        dto.setStartsAt(LocalDateTime.now().minusDays(1));

        //When + Then
        mockMvc.perform(put("/api/appointments/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.startsAt").exists());

        then(appointmentService).shouldHaveNoInteractions();
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void updateAppointment_whenCustomerDoesNotExist_shouldReturn404() throws Exception{
        //Given
        Long id = 5L;
        var dto = getAppointmentDto(id);
        var entity = getAppointmentEntity(id);

        given(appointmentMapper.toEntity(dto)).willReturn(entity);
                willThrow(new ResourceNotFoundException("Customer not found."))
                .given(appointmentService)
                .updateAppointment(entity, id);

        //When + Then
        mockMvc.perform(put("/api/appointments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."));

        then(appointmentMapper).should().toEntity(any(AppointmentRequestDto.class));
        then(appointmentService).should().updateAppointment(entity, id);
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

    @Test
    void deleteAppointment_withNonExistingId_shouldReturn404() throws Exception{
        //Given
        Long id = 999L;
        willThrow(new ResourceNotFoundException("Appointment not found"))
                .given(appointmentService)
                .deleteAppointment(id);

        //When + Then
        mockMvc.perform(delete("/api/appointments/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"));

        then(appointmentService).should().deleteAppointment(id);
    }

}



package com.turnero.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.AppointmentCancelRequestDto;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.dto.AppointmentUpdateRequestDto;
import com.turnero.api.exception.InvalidStateTransitionException;
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

    private final static String BASE_URL = "/api/v1/appointments";

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

    private AppointmentUpdateRequestDto getAppointmentUpdateRequestDto(Long id){
        return AppointmentUpdateRequestDto.builder()
                .customerId(id)
                .staffMemberId(3L)
                .serviceOfferingId(2L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .customerNotes("Notes")
                .internalNotes("VIP Customer")
                .build();
    }

    @Test
    void saveAppointment_ok_shouldReturn201_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var dto = getAppointmentDto(id);
        var responseDto = getAppointmentResponseDto(id);

        given(appointmentService.saveAppointment(any(AppointmentRequestDto.class)))
                .willReturn(responseDto);

        // When
        mockMvc.perform(post(BASE_URL)
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
        then(appointmentService).should().saveAppointment(any(AppointmentRequestDto.class));
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void saveAppointment_withInvalidDto_shouldReturn400() throws Exception{
        // Given
        Long id = 1L;
        AppointmentRequestDto dto = getAppointmentDto(id);
        dto.setCustomerId(null);

        // When + Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("customerId"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());

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
                .saveAppointment(any(AppointmentRequestDto.class));

        // When + Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."));

        then(appointmentService).should().saveAppointment(any(AppointmentRequestDto.class));
        then(appointmentMapper).shouldHaveNoInteractions();
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
        mockMvc.perform(get(BASE_URL))
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
        mockMvc.perform(get(BASE_URL + "/{id}", id))
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
        mockMvc.perform(get(BASE_URL + "/{id}", id))
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
        AppointmentUpdateRequestDto request = getAppointmentUpdateRequestDto(id);
        AppointmentResponseDto response = getAppointmentResponseDto(id);

        given(appointmentService.updateAppointment(eq(id), any(AppointmentUpdateRequestDto.class)))
                .willReturn(response);

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.customerId").value(5))
                .andExpect(jsonPath("$.serviceOfferingId").value(2))
                .andExpect(jsonPath("$.staffMemberId").value(3));

        then(appointmentService).should().updateAppointment(eq(id), any(AppointmentUpdateRequestDto.class));
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void updateAppointment_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        Long id = 5L;
        AppointmentUpdateRequestDto request = getAppointmentUpdateRequestDto(id);
        request.setStartsAt(LocalDateTime.now().minusDays(1));


        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("startsAt"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL + "/" + id))
                .andExpect(jsonPath("$.path").value(BASE_URL + "/5"))

                .andExpect(jsonPath("$.timestamp").exists());

        then(appointmentService).shouldHaveNoInteractions();
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void updateAppointment_whenCustomerDoesNotExist_shouldReturn404() throws Exception{
        //Given
        Long id = 5L;
        AppointmentUpdateRequestDto request = getAppointmentUpdateRequestDto(id);

        given(appointmentService.updateAppointment(eq(id), any(AppointmentUpdateRequestDto.class))).
                willThrow(new ResourceNotFoundException("Customer not found."));


        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."));

        then(appointmentService).should().updateAppointment(eq(id), any(AppointmentUpdateRequestDto.class));
        then(appointmentMapper).shouldHaveNoInteractions();
    }

    @Test
    void confirmAppointment_shouldReturnOk() throws Exception {
        Long id = 1L;

        AppointmentResponseDto response = AppointmentResponseDto.builder()
                .id(id)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(appointmentService.confirmAppointment(id))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(appointmentService).confirmAppointment(id);
    }

    @Test
    void confirmAppointment_whenInvalidTransition_shouldReturnConflict() throws Exception {
        Long id = 1L;

        when(appointmentService.confirmAppointment(id)).thenThrow(new InvalidStateTransitionException(
                        "Cannot transition appointment from CANCELLED to CONFIRMED"));

        mockMvc.perform(post(BASE_URL + "/{id}/confirm", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Cannot transition appointment from CANCELLED to CONFIRMED"))
                .andExpect(jsonPath("$.path").value(BASE_URL + "/1/confirm"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(appointmentService).confirmAppointment(id);
    }

    @Test
    void cancelAppointment_shouldReturnOk() throws Exception {
        Long id = 1L;

        AppointmentCancelRequestDto request = AppointmentCancelRequestDto.builder()
                .cancellationReason("Customer requested cancellation")
                .build();

        AppointmentResponseDto response = AppointmentResponseDto.builder()
                .id(id)
                .status(AppointmentStatus.CANCELLED)
                .cancellationReason("Customer requested cancellation")
                .build();

        when(appointmentService.cancelAppointment(eq(id), any(AppointmentCancelRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason")
                        .value("Customer requested cancellation"));

        verify(appointmentService).cancelAppointment(eq(id), any(AppointmentCancelRequestDto.class));
    }


    @Test
    void deleteAppointment_ok_shouldReturn200_andCallService() throws Exception{
        //Given
        Long id = 7L;

        //When + Then
        mockMvc.perform(delete(BASE_URL + "/7"))
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
        mockMvc.perform(delete(BASE_URL + "/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"));

        then(appointmentService).should().deleteAppointment(id);
    }

}



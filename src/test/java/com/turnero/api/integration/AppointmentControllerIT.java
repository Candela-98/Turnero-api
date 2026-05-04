package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.controller.AppointmentController;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.AppointmentStatus;
import com.turnero.api.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AppointmentControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppointmentMapper appointmentMapper;

    @Autowired
    AppointmentController appointmentController;

    @Autowired
    AppointmentRepository appointmentRepository;

    @BeforeEach
    void cleanDb() {
        appointmentRepository.deleteAll();
    }

    private AppointmentRequestDto getAppointmentRequestDto() {
        return AppointmentRequestDto.builder()
                .customerId(1L)
                .serviceId(1L)
                .staffMemberId(1L)
                .dateTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .notes("Test appointment")
                .status(AppointmentStatus.PENDING)
                .build();
    }

    private Appointment getAppointment() {
        return Appointment.builder()
                .customerId(1L)
                .serviceId(1L)
                .staffMemberId(1L)
                .dateTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .notes("Test appointment")
                .build();
    }

    private AppointmentResponseDto getAppointmentResponseDto() {
        return AppointmentResponseDto.builder()
                .id(1L)
                .customerId(1L)
                .serviceId(1L)
                .staffMemberId(1L)
                .dateTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .notes("Test appointment")
                .build();
    }

    @Test
    void saveAppointment_whenRequestIsValid_persistsAppointment_andReturns201() throws Exception {
        //Given
        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setDateTime(LocalDateTime.now().plusDays(1));
        dto.setStatus(AppointmentStatus.PENDING);

        // When
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        List<Appointment> appointments = appointmentRepository.findAll();

        assertThat(appointments).hasSize(1);
        Appointment saved = appointments.get(0);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCustomerId()).isEqualTo(1L);
        assertThat(saved.getServiceId()).isEqualTo(1L);
        assertThat(saved.getStaffMemberId()).isEqualTo(1L);
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getNotes()).isEqualTo("Test appointment");

        String json = result.getResponse().getContentAsString();
        AppointmentResponseDto response = objectMapper.readValue(json, AppointmentResponseDto.class);
        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getCustomerId()).isEqualTo(saved.getCustomerId());
        assertThat(response.getServiceId()).isEqualTo(saved.getServiceId());
        assertThat(response.getStaffMemberId()).isEqualTo(saved.getStaffMemberId());
        assertThat(response.getDateTime()).isEqualTo(saved.getDateTime());
        assertThat(response.getDurationMinutes()).isEqualTo(saved.getDurationMinutes());
        assertThat(response.getNotes()).isEqualTo(saved.getNotes());
    }

    @Test
    void saveAppointment_whenCustomerIdIsNull_returns400() throws Exception {
        //Given
        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setCustomerId(null);

        //When + Then
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.customerId").exists());

        //Then
        assertThat(appointmentRepository.findAll()).isEmpty();
    }

    @Test
    void findAppointment_whenAppointmentExists_returns200AndAppointment() throws Exception {
        //Given
        Appointment appointment = getAppointment();
        Appointment saved = appointmentRepository.save(appointment);

        // When
        MvcResult result = mockMvc.perform(get("/api/appointments/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        AppointmentResponseDto response = objectMapper.readValue(json, AppointmentResponseDto.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getCustomerId()).isEqualTo(saved.getCustomerId());
        assertThat(response.getServiceId()).isEqualTo(saved.getServiceId());
        assertThat(response.getStaffMemberId()).isEqualTo(saved.getStaffMemberId());
        assertThat(response.getDateTime()).isEqualTo(saved.getDateTime());
        assertThat(response.getDurationMinutes()).isEqualTo(saved.getDurationMinutes());
        assertThat(response.getNotes()).isEqualTo(saved.getNotes());

    }

    @Test
    void findAppointment_whenAppointmentDoesNotExist_returns404() throws Exception{
        //Given
        Long id = 999L;

        mockMvc.perform(get("/api/appointments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found with ID: 999"));
    }

    @Test
    void updateAppointment_whenRequestIsValid_updatesAppointment_andReturns204() throws Exception{
        //Given
        Appointment appointment = getAppointment();
        appointment.setDateTime(LocalDateTime.now().plusDays(1));
        appointment.setStatus(AppointmentStatus.PENDING);

        Appointment saved = appointmentRepository.save(appointment);

        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setDateTime(LocalDateTime.now().plusDays(2));
        dto.setDurationMinutes(30);
        dto.setStatus(AppointmentStatus.CONFIRMED); // o el que tengas
        dto.setNotes("Updated appointment");

        // When
        mockMvc.perform(put("/api/appointments/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        Appointment updated = appointmentRepository.findById(saved.getId()).orElseThrow();

        assertThat(updated.getDateTime()).isEqualTo(dto.getDateTime());
        assertThat(updated.getDurationMinutes()).isEqualTo(30);
        assertThat(updated.getNotes()).isEqualTo("Updated appointment");
    }

    @Test
    void udpateAppointment_whenCustomerIdIsNull_returns400() throws Exception{
        //Given
        Appointment appointment = getAppointment();
        Appointment saved = appointmentRepository.save(appointment);

        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setCustomerId(null);

        // When + Then
        mockMvc.perform(put("/api/appointments/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.customerId").exists());

        assertThat(appointmentRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void listAppointments_whenAppointmentsExist_returns200AndAppointmentList() throws Exception {
        // Given
        Appointment appointment1 = getAppointment();
        Appointment appointment2 = getAppointment();
        appointment2.setCustomerId(2L);
        appointment2.setServiceId(2L);
        appointment2.setStaffMemberId(2L);
        appointment2.setDateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        appointment2.setDurationMinutes(30);
        appointment2.setNotes("Second appointment");

        appointmentRepository.save(appointment1);
        appointmentRepository.save(appointment2);

        // When
        MvcResult result = mockMvc.perform(get("/api/appointments")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        //Then
        String json = result.getResponse().getContentAsString();
        List<AppointmentResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(2);
        assertThat(response).extracting(AppointmentResponseDto::getCustomerId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(response).extracting(AppointmentResponseDto::getServiceId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(response).extracting(AppointmentResponseDto::getStaffMemberId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(response).extracting(AppointmentResponseDto::getDateTime)
                .containsExactlyInAnyOrder(appointment1.getDateTime(), appointment2.getDateTime());
        assertThat(response).extracting(AppointmentResponseDto::getDurationMinutes)
                .containsExactlyInAnyOrder(60, 30);
        assertThat(response).extracting(AppointmentResponseDto::getNotes)
                .containsExactlyInAnyOrder("Test appointment", "Second appointment");

    }

    @Test
    void listAppointments_whenNoAppointmentsExist_returns200AndEmptyList() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/appointments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AppointmentResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).isEmpty();
    }

    @Test
    void deleteAppointment_whenAppointmentExists_deletesAppointment_andReturns204() throws Exception{
        //Given
        Appointment appointment = getAppointment();
        Appointment saved = appointmentRepository.save(appointment);

        //When
        mockMvc.perform(delete("/api/appointments/{id}", saved.getId()))
                .andExpect(status().isNoContent());

        //Then
        assertThat(appointmentRepository.existsById(saved.getId())).isFalse();
    }

    @Test
    void deleteAppointment_whenAppointmentDoesNotExist_returns404() throws Exception{
        //Given
        Long id = 999L;

        // When + Then
        mockMvc .perform(delete("/api/appointments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found with ID: 999"));
    }
}
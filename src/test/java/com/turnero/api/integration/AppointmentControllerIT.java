package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.controller.AppointmentController;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
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

    @Autowired
    ServOfferingRepository servOfferingRepository;

    @Autowired
    StaffMemberRepository staffMemberRepository;

    @Autowired
    CustomerRepository customerRepository;

    @BeforeEach
    void cleanDb() {
        appointmentRepository.deleteAll();
        customerRepository.deleteAll();
        staffMemberRepository.deleteAll();
        servOfferingRepository.deleteAll();
    }

    private AppointmentRequestDto getAppointmentRequestDto() {
        return AppointmentRequestDto.builder()
                .customerId(1L)
                .serviceOfferingId(1L)
                .staffMemberId(1L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .customerNotes("Test appointment")
                .status(AppointmentStatus.PENDING)
                .build();
    }

    private Appointment getAppointment() {
        LocalDateTime auditDate = LocalDateTime.now().minusDays(1);
        return Appointment.builder()
                .customerId(1L)
                .serviceOfferingId(1L)
                .staffMemberId(1L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .customerNotes("Test appointment")
                .createdAt(auditDate)
                .updatedAt(auditDate)
                .build();
    }

    private AppointmentResponseDto getAppointmentResponseDto() {
        return AppointmentResponseDto.builder()
                .id(1L)
                .customerId(1L)
                .serviceOfferingId(1L)
                .staffMemberId(1L)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .customerNotes("Test appointment")
                .build();
    }

    private Customer getCustomerEntity() {
        return Customer.builder()
                .name("Juan Olmedo")
                .email("juan.olmedo@mail.com")
                .phoneNumber("123456789")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private StaffMember getStaffMemberEntity() {
        return StaffMember.builder()
                .name("Maria Gomez")
                .specialty("Corte")
                .build();
    }

    private ServiceOffering getServiceOfferingEntity() {
        return ServiceOffering.builder()
                .name("Corte")
                .durationMinutes(60)
                .priceCents(15000)
                .build();
    }

    @Test
    void saveAppointment_whenRequestIsValid_persistsAppointment_andReturns201() throws Exception {
        //Given
        AppointmentRequestDto dto = getAppointmentRequestDto();
        Customer customer = customerRepository.save(getCustomerEntity());
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        dto.setStartsAt(LocalDateTime.now().plusDays(1));
        dto.setStatus(AppointmentStatus.PENDING);
        dto.setCustomerId(customer.getId());
        dto.setServiceOfferingId(serviceOffering.getId());
        dto.setStaffMemberId(staffMember.getId());

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
        assertThat(saved.getCustomerId()).isEqualTo(customer.getId());
        assertThat(saved.getServiceOfferingId()).isEqualTo(serviceOffering.getId());
        assertThat(saved.getStaffMemberId()).isEqualTo(staffMember.getId());
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getCustomerNotes()).isEqualTo("Test appointment");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        String json = result.getResponse().getContentAsString();
        AppointmentResponseDto response = objectMapper.readValue(json, AppointmentResponseDto.class);
        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getCustomerId()).isEqualTo(saved.getCustomerId());
        assertThat(response.getServiceOfferingId()).isEqualTo(saved.getServiceOfferingId());
        assertThat(response.getStaffMemberId()).isEqualTo(saved.getStaffMemberId());
        assertThat(response.getStartsAt()).isEqualTo(saved.getStartsAt());
        assertThat(response.getDurationMinutes()).isEqualTo(saved.getDurationMinutes());
        assertThat(response.getCustomerNotes()).isEqualTo(saved.getCustomerNotes());
        assertThat(response.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
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

    // El mismo test se puede hacer para serviceOffering y staffMember, pero por brevedad solo se muestra para customer
    @Test
    void saveAppointment_whenCustomerDoesNotExist_returns404() throws Exception {
        //Given
        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setCustomerId(999L);

        //When + Then
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."));

        //Then
        assertThat(appointmentRepository.findAll()).isEmpty();
    }

    @Test
    void saveAppointment_whenAppointmentsOverlap_shouldReturn409() throws Exception {

        // Given
        Customer customer1 = customerRepository.save(getCustomerEntity());
        Customer customer2 = customerRepository.save(
                Customer.builder()
                        .name("Pedro Gomez")
                        .email("pedro.gomez@mail.com")
                        .phoneNumber("987654321")
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        ServiceOffering service1 = servOfferingRepository.save(getServiceOfferingEntity());
        ServiceOffering service2 = servOfferingRepository.save(
                ServiceOffering.builder()
                        .name("Barba")
                        .durationMinutes(30)
                        .priceCents(10000)
                        .build()
        );

        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());

        LocalDateTime start = LocalDateTime.now()
                .plusDays(1)
                .withHour(10)
                .withMinute(0);

        Appointment existingAppointment = Appointment.builder()
                .customerId(customer1.getId())
                .serviceOfferingId(service1.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(start)
                .durationMinutes(30)
                .status(AppointmentStatus.PENDING)
                .build();

        appointmentRepository.save(existingAppointment);

        AppointmentRequestDto dto = AppointmentRequestDto.builder()
                .customerId(customer2.getId())
                .serviceOfferingId(service2.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(start.plusMinutes(15))
                .durationMinutes(30)
                .status(AppointmentStatus.PENDING)
                .build();

        // When + Then
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Staff member already has an appointment in this time range"));
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
        assertThat(response.getServiceOfferingId()).isEqualTo(saved.getServiceOfferingId());
        assertThat(response.getStaffMemberId()).isEqualTo(saved.getStaffMemberId());
        assertThat(response.getStartsAt()).isEqualTo(saved.getStartsAt());
        assertThat(response.getDurationMinutes()).isEqualTo(saved.getDurationMinutes());
        assertThat(response.getCustomerNotes()).isEqualTo(saved.getCustomerNotes());
        assertThat(response.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());

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
        Customer customer = customerRepository.save(getCustomerEntity());
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());

        Appointment appointment = getAppointment();
        appointment.setCustomerId(customer.getId());
        appointment.setServiceOfferingId(serviceOffering.getId());
        appointment.setStaffMemberId(staffMember.getId());
        appointment.setStartsAt(LocalDateTime.now().plusDays(1));
        appointment.setStatus(AppointmentStatus.PENDING);

        Appointment saved = appointmentRepository.save(appointment);

        LocalDateTime originalCreatedAt = saved.getCreatedAt();
        LocalDateTime originalUpdatedAt = saved.getUpdatedAt();

        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setCustomerId(customer.getId());
        dto.setServiceOfferingId(serviceOffering.getId());
        dto.setStaffMemberId(staffMember.getId());
        dto.setStartsAt(LocalDateTime.now().plusDays(2));
        dto.setDurationMinutes(30);
        dto.setStatus(AppointmentStatus.CONFIRMED); // o el que tengas
        dto.setCustomerNotes("Updated appointment");

        // When
        mockMvc.perform(put("/api/appointments/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        Appointment updated = appointmentRepository.findById(saved.getId()).orElseThrow();

        assertThat(updated.getStartsAt()).isEqualTo(dto.getStartsAt());
        assertThat(updated.getDurationMinutes()).isEqualTo(30);
        assertThat(updated.getCustomerNotes()).isEqualTo("Updated appointment");
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
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

    // El mismo test se puede hacer para serviceOffering y staffMember, pero por brevedad solo se muestra para customer
    @Test
    void updateAppointment_whenCustomerDoesNotExist_returns404() throws Exception{
        //Given
        Appointment appointment = getAppointment();
        Appointment saved = appointmentRepository.save(appointment);

        AppointmentRequestDto dto = getAppointmentRequestDto();
        dto.setCustomerId(999L);

        // When + Then
        mockMvc.perform(put("/api/appointments/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found."));

        assertThat(appointmentRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void listAppointments_whenAppointmentsExist_returns200AndAppointmentList() throws Exception {
        // Given
        Appointment appointment1 = getAppointment();
        Appointment appointment2 = getAppointment();
        appointment2.setCustomerId(2L);
        appointment2.setServiceOfferingId(2L);
        appointment2.setStaffMemberId(2L);
        appointment2.setStartsAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        appointment2.setDurationMinutes(30);
        appointment2.setCustomerNotes("Second appointment");

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
        assertThat(response).extracting(AppointmentResponseDto::getServiceOfferingId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(response).extracting(AppointmentResponseDto::getStaffMemberId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(response).extracting(AppointmentResponseDto::getStartsAt)
                .containsExactlyInAnyOrder(appointment1.getStartsAt(), appointment2.getStartsAt());
        assertThat(response).extracting(AppointmentResponseDto::getDurationMinutes)
                .containsExactlyInAnyOrder(60, 30);
        assertThat(response).extracting(AppointmentResponseDto::getCustomerNotes)
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


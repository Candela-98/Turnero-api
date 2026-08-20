package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.dto.StaffWorkingHoursRequestDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.StaffWorkingHours;
import com.turnero.api.model.User;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AppointmentSource;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.BusinessHoursRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffWorkingHoursRepository;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.StaffMemberService;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class StaffMemberControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StaffMemberMapper staffMemberMapper;

    @Autowired
    StaffMemberService staffMemberService;

    @Autowired
    StaffMemberRepository staffMemberRepository;

    @Autowired
    StaffWorkingHoursRepository staffWorkingHoursRepository;

    @Autowired
    AppointmentRepository appointmentRepository;

    @Autowired
    BusinessHoursRepository businessHoursRepository;

    @Autowired
    UserRepository userRepository;

    private static final String BASE_URL = "/api/v1/staff-members";

    @BeforeEach
    void cleanDb() {
        appointmentRepository.deleteAll();
        staffWorkingHoursRepository.deleteAll();
        staffMemberRepository.deleteAll();
    }

    private StaffMemberRequestDto getStaffMemberRequestDto() {
        return StaffMemberRequestDto.builder()
                .userId(20L)
                .name("Matias")
                .roleLabel("Senior barber")
                .specialty("Barber")
                .avatarUrl("https://example.com/avatar.png")
                .build();
    }

    private StaffMember getStaffMember() {
         return StaffMember.builder()
                 .businessId(1L)
                 .userId(20L)
                 .name("Matias")
                 .roleLabel("Senior barber")
                 .specialty("Barber")
                 .avatarUrl("https://example.com/avatar.png")
                 .status(StaffMemberStatus.ACTIVE)
                 .build();
    }

    private void createBusinessHours(Long businessId) {
        businessHoursRepository.save(BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(18, 0))
                .isClosed(false)
                .build());
    }

    private User createUser(Long businessId) {
        return userRepository.save(User.builder()
                .businessId(businessId)
                .name("Test User")
                .email("matias@mail.com")
                .role(UserRole.STAFF)
                .build());
    }

    private Appointment createAppointment(
            Long businessId,
            Long staffMemberId,
            AppointmentStatus appointmentStatus,
            LocalDateTime startsAt
    ) {
        return appointmentRepository.save(Appointment.builder()
                .businessId(businessId)
                .customerId(1L)
                .serviceOfferingId(1L)
                .staffMemberId(staffMemberId)
                .startsAt(startsAt)
                .endsAt(startsAt.plusMinutes(30))
                .durationMinutes(30)
                .priceCents(10000)
                .status(appointmentStatus)
                .source(AppointmentSource.ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private List<StaffWorkingHoursRequestDto> fullWeekRequest(LocalTime startsAt, LocalTime endsAt) {
        return List.of(
                workingHoursRequest(DayOfWeek.MONDAY, startsAt, endsAt, true),
                workingHoursRequest(DayOfWeek.TUESDAY, startsAt, endsAt, true),
                workingHoursRequest(DayOfWeek.WEDNESDAY, startsAt, endsAt, true),
                workingHoursRequest(DayOfWeek.THURSDAY, startsAt, endsAt, true),
                workingHoursRequest(DayOfWeek.FRIDAY, startsAt, endsAt, true),
                workingHoursRequest(DayOfWeek.SATURDAY, null, null, false),
                workingHoursRequest(DayOfWeek.SUNDAY, null, null, false)
        );
    }

    private StaffWorkingHoursRequestDto workingHoursRequest(
            DayOfWeek dayOfWeek,
            LocalTime startsAt,
            LocalTime endsAt,
            boolean isAvailable
    ) {
        return StaffWorkingHoursRequestDto.builder()
                .dayOfWeek(dayOfWeek)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .isAvailable(isAvailable)
                .build();
    }

    private void createFullWeekWorkingHours(Long staffMemberId, LocalTime startsAt, LocalTime endsAt) {
        List<StaffWorkingHours> workingHours = List.of(
                workingHoursEntity(staffMemberId, DayOfWeek.MONDAY, startsAt, endsAt, true),
                workingHoursEntity(staffMemberId, DayOfWeek.TUESDAY, startsAt, endsAt, true),
                workingHoursEntity(staffMemberId, DayOfWeek.WEDNESDAY, startsAt, endsAt, true),
                workingHoursEntity(staffMemberId, DayOfWeek.THURSDAY, startsAt, endsAt, true),
                workingHoursEntity(staffMemberId, DayOfWeek.FRIDAY, startsAt, endsAt, true),
                workingHoursEntity(staffMemberId, DayOfWeek.SATURDAY, null, null, false),
                workingHoursEntity(staffMemberId, DayOfWeek.SUNDAY, null, null, false)
        );

        staffWorkingHoursRepository.saveAll(workingHours);
    }

    private StaffWorkingHours workingHoursEntity(
            Long staffMemberId,
            DayOfWeek dayOfWeek,
            LocalTime startsAt,
            LocalTime endsAt,
            boolean isAvailable
    ) {
        return StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(dayOfWeek)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .isAvailable(isAvailable)
                .build();
    }

    private JsonNode findJsonDay(JsonNode workingHours, DayOfWeek dayOfWeek) {
        for (JsonNode workingHour : workingHours) {
            if (dayOfWeek.name().equals(workingHour.get("day_of_week").asText())) {
                return workingHour;
            }
        }

        throw new AssertionError("Missing working hours for " + dayOfWeek);
    }

    private StaffWorkingHours findPersistedDay(List<StaffWorkingHours> workingHours, DayOfWeek dayOfWeek) {
        return workingHours.stream()
                .filter(workingHour -> workingHour.getDayOfWeek() == dayOfWeek)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing working hours for " + dayOfWeek));
    }

    @Test
    void saveStaffMember_whenRequestIsValid_persistsStaffMember_andReturns201() throws Exception {
        //Given
        StaffMemberRequestDto dto = getStaffMemberRequestDto();
        createBusinessHours(1L);

        User savedUser = createUser(1L);
        dto.setUserId(savedUser.getId());

        //When
        MvcResult result = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        List<StaffMember> staffMembers = staffMemberRepository.findAll();

        assertThat(staffMembers).hasSize(1);
        StaffMember saved = staffMembers.get(0);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBusinessId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(savedUser.getId());
        assertThat(saved.getName()).isEqualTo("Matias");
        assertThat(saved.getRoleLabel()).isEqualTo("Senior barber");
        assertThat(saved.getSpecialty()).isEqualTo("Barber");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(saved.getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);

        String json = result.getResponse().getContentAsString();
        StaffMemberResponseDto response = objectMapper.readValue(json, StaffMemberResponseDto.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getName()).isEqualTo("Matias");
        assertThat(response.getSpecialty()).isEqualTo("Barber");
    }

    @Test
    void saveStaffMember_whenNameIsBlank_returns400() throws Exception {
        // Given
        StaffMemberRequestDto dto = getStaffMemberRequestDto();
        dto.setName("");

        // When + Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(staffMemberRepository.findAll()).isEmpty();
    }

    @Test
    void findStaffMember_whenExists_returns200AndStaffMember() throws Exception {
        // Given
        StaffMember saved = staffMemberRepository.save(getStaffMember());

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        StaffMemberResponseDto response = objectMapper.readValue(json, StaffMemberResponseDto.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getName()).isEqualTo("Matias");
    }

    @Test
    void findStaffMember_whenDoesNotExist_returns404() throws Exception {
        // Given
        Long id = 999L;

        // When + Then
        mockMvc.perform(get(BASE_URL + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));
    }

    @Test
    void findStaffMember_whenOutsideBusinessScope_returns404() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        staffMember.setBusinessId(2L);
        StaffMember saved = staffMemberRepository.save(staffMember);

        // When + Then
        mockMvc.perform(get(BASE_URL + "/{id}", saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: " + saved.getId()));
    }

    @Test
    void updateStaffMember_whenRequestIsValid_updatesStaffMember_andReturns200() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        StaffMember saved = staffMemberRepository.save(staffMember);

        StaffMemberUpdateRequestDto dto = StaffMemberUpdateRequestDto.builder()
                .name("Matias Updated")
                .roleLabel("Lead barber")
                .specialty("Barber Updated")
                .avatarUrl("https://example.com/avatar-updated.png")
                .status(StaffMemberStatus.INACTIVE)
                .build();

        // When
        mockMvc.perform(patch(BASE_URL + "/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Matias Updated"))
                .andExpect(jsonPath("$.role_label").value("Lead barber"))
                .andExpect(jsonPath("$.specialty").value("Barber Updated"))
                .andExpect(jsonPath("$.avatar_url").value("https://example.com/avatar-updated.png"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Then
        StaffMember updated = staffMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Matias Updated");
        assertThat(updated.getRoleLabel()).isEqualTo("Lead barber");
        assertThat(updated.getSpecialty()).isEqualTo("Barber Updated");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/avatar-updated.png");
        assertThat(updated.getStatus()).isEqualTo(StaffMemberStatus.INACTIVE);
        assertThat(updated.getBusinessId()).isEqualTo(1L);
        assertThat(updated.getUserId()).isEqualTo(20L);
    }

    @Test
    void updateStaffMember_whenRequestIsPartial_updatesOnlyProvidedFields() throws Exception {
        // Given
        StaffMember saved = staffMemberRepository.save(getStaffMember());

        StaffMemberUpdateRequestDto dto = StaffMemberUpdateRequestDto.builder()
                .roleLabel("Lead barber")
                .build();

        // When
        mockMvc.perform(patch(BASE_URL + "/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Matias"))
                .andExpect(jsonPath("$.role_label").value("Lead barber"))
                .andExpect(jsonPath("$.specialty").value("Barber"))
                .andExpect(jsonPath("$.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Then
        StaffMember updated = staffMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Matias");
        assertThat(updated.getRoleLabel()).isEqualTo("Lead barber");
        assertThat(updated.getSpecialty()).isEqualTo("Barber");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(updated.getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);
    }

    @Test
    void updateStaffMember_whenNameIsBlank_returns400() throws Exception {
        // Given
        StaffMember saved = staffMemberRepository.save(getStaffMember());

        StaffMemberUpdateRequestDto dto = StaffMemberUpdateRequestDto.builder()
                .name("")
                .build();

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL + "/" + saved.getId()))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void updateStaffMember_whenDoesNotExist_returns404() throws Exception {
        // Given
        StaffMemberUpdateRequestDto dto = StaffMemberUpdateRequestDto.builder()
                .name("Matias Updated")
                .build();

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));
    }

    @Test
    void updateStaffMember_whenOutsideBusinessScope_returns404AndDoesNotModifyStaffMember() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        staffMember.setBusinessId(2L);
        StaffMember saved = staffMemberRepository.save(staffMember);

        StaffMemberUpdateRequestDto dto = StaffMemberUpdateRequestDto.builder()
                .name("Matias Updated")
                .build();

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: " + saved.getId()));

        StaffMember unchanged = staffMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Matias");
        assertThat(unchanged.getBusinessId()).isEqualTo(2L);
    }

    @Test
    void listStaffMembers_whenExist_returns200AndList() throws Exception {
        // Given
        staffMemberRepository.save(getStaffMember());

        StaffMember second = new StaffMember();
        second.setBusinessId(1L);
        second.setName("Maria");
        second.setSpecialty("Colorista");

        staffMemberRepository.save(second);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<StaffMemberResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(response)
                .extracting(StaffMemberResponseDto::getName)
                .containsExactlyInAnyOrder("Matias", "Maria");

        assertThat(response)
                .extracting(StaffMemberResponseDto::getSpecialty)
                .containsExactlyInAnyOrder("Barber", "Colorista");
    }

    @Test
    void listStaffMembers_whenNoStaffMembersExist_returns200AndEmptyList() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<StaffMember> response = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(response).isEmpty();
    }

    @Test
    void updateStaffMember_whenAppointmentExists_shouldPreserveHistoricalAppointment() throws Exception {
        // Given
        StaffMember savedStaffMember = staffMemberRepository.save(getStaffMember());
        var startsAt = java.time.LocalDateTime.now().plusDays(1);

        Appointment appointment = Appointment.builder()
                .businessId(1L)
                .customerId(1L)
                .serviceOfferingId(1L)
                .staffMemberId(savedStaffMember.getId())
                .startsAt(startsAt)
                .endsAt(startsAt.plusMinutes(30))
                .durationMinutes(30)
                .priceCents(10000)
                .status(AppointmentStatus.PENDING)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);

        StaffMemberUpdateRequestDto updateRequest = StaffMemberUpdateRequestDto.builder()
                .name("Matias Updated")
                .status(StaffMemberStatus.INACTIVE)
                .build();

        // When
        mockMvc.perform(patch(BASE_URL + "/{id}", savedStaffMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Matias Updated"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Then
        Appointment unchangedAppointment = appointmentRepository.findById(savedAppointment.getId()).orElseThrow();
        assertThat(unchangedAppointment.getStaffMemberId()).isEqualTo(savedStaffMember.getId());
        assertThat(unchangedAppointment.getStartsAt()).isEqualTo(startsAt);
        assertThat(unchangedAppointment.getEndsAt()).isEqualTo(startsAt.plusMinutes(30));
        assertThat(unchangedAppointment.getDurationMinutes()).isEqualTo(30);
        assertThat(unchangedAppointment.getPriceCents()).isEqualTo(10000);
        assertThat(unchangedAppointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void deleteStaffMember_whenStaffMemberExists_deactivatesWithoutDeleting_andReturns204() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        StaffMember saved = staffMemberRepository.save(staffMember);
        Long id = saved.getId();

        // When
        mockMvc.perform(delete(BASE_URL + "/{id}", id))
                .andExpect(status().isNoContent());

        // Then
        StaffMember deactivated = staffMemberRepository.findById(id).orElseThrow();
        assertThat(deactivated.getStatus()).isEqualTo(StaffMemberStatus.INACTIVE);
        assertThat(staffMemberRepository.existsById(saved.getId())).isTrue();
    }

    @Test
    void deleteStaffMember_whenStaffMemberDoesNotExist_returns404() throws Exception {
        // Given
        Long id = 999L;

        // When + Then
        mockMvc.perform(delete(BASE_URL + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));
    }

    @Test
    void deleteStaffMember_whenStaffMemberIsOutsideBusinessScope_returns404AndDoesNotModifyStaffMember() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        staffMember.setBusinessId(2L);
        StaffMember saved = staffMemberRepository.save(staffMember);

        // When + Then
        mockMvc.perform(delete(BASE_URL + "/{id}", saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: " + saved.getId()));

        StaffMember unchanged = staffMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);
        assertThat(unchanged.getBusinessId()).isEqualTo(2L);
    }

    @Test
    void deleteStaffMember_whenFuturePendingAppointmentExists_returns409AndDoesNotDeactivate() throws Exception {
        assertDeleteBlockedByFutureActiveAppointment(AppointmentStatus.PENDING);
    }

    @Test
    void deleteStaffMember_whenFutureConfirmedAppointmentExists_returns409AndDoesNotDeactivate() throws Exception {
        assertDeleteBlockedByFutureActiveAppointment(AppointmentStatus.CONFIRMED);
    }

    @Test
    void deleteStaffMember_whenPastPendingAppointmentExists_deactivatesAndPreservesAppointment() throws Exception {
        // Given
        StaffMember savedStaffMember = staffMemberRepository.save(getStaffMember());
        LocalDateTime startsAt = LocalDateTime.now().minusDays(1);
        Appointment savedAppointment = createAppointment(
                1L,
                savedStaffMember.getId(),
                AppointmentStatus.PENDING,
                startsAt
        );

        // When
        mockMvc.perform(delete(BASE_URL + "/{id}", savedStaffMember.getId()))
                .andExpect(status().isNoContent());

        // Then
        StaffMember deactivated = staffMemberRepository.findById(savedStaffMember.getId()).orElseThrow();
        Appointment historicalAppointment = appointmentRepository.findById(savedAppointment.getId()).orElseThrow();

        assertThat(deactivated.getStatus()).isEqualTo(StaffMemberStatus.INACTIVE);
        assertThat(historicalAppointment.getStaffMemberId()).isEqualTo(savedStaffMember.getId());
        assertThat(historicalAppointment.getStartsAt()).isEqualTo(startsAt);
        assertThat(historicalAppointment.getEndsAt()).isEqualTo(startsAt.plusMinutes(30));
        assertThat(historicalAppointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void deleteStaffMember_whenFutureCancelledAppointmentExists_deactivatesAndPreservesAppointment() throws Exception {
        assertDeleteAllowedWithFutureNonBlockingAppointment(AppointmentStatus.CANCELLED);
    }

    @Test
    void deleteStaffMember_whenFutureCompletedAppointmentExists_deactivatesAndPreservesAppointment() throws Exception {
        assertDeleteAllowedWithFutureNonBlockingAppointment(AppointmentStatus.COMPLETED);
    }

    @Test
    void getWorkingHours_whenWeekExists_returnsStaffWeek() throws Exception {
        StaffMember saved = staffMemberRepository.save(getStaffMember());
        createFullWeekWorkingHours(saved.getId(), LocalTime.of(9, 0), LocalTime.of(18, 0));

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{id}/working-hours", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(7))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode monday = findJsonDay(response, DayOfWeek.MONDAY);
        JsonNode saturday = findJsonDay(response, DayOfWeek.SATURDAY);

        assertThat(monday.has("day_of_week")).isTrue();
        assertThat(monday.has("starts_at")).isTrue();
        assertThat(monday.has("ends_at")).isTrue();
        assertThat(monday.has("is_available")).isTrue();
        assertThat(monday.has("available")).isFalse();
        assertThat(monday.get("starts_at").asText()).isEqualTo("09:00");
        assertThat(monday.get("ends_at").asText()).isEqualTo("18:00");
        assertThat(monday.get("is_available").asBoolean()).isTrue();
        assertThat(saturday.get("starts_at").isNull()).isTrue();
        assertThat(saturday.get("ends_at").isNull()).isTrue();
        assertThat(saturday.get("is_available").asBoolean()).isFalse();
    }

    @Test
    void replaceWorkingHours_whenRequestIsValid_replacesSevenRecordsInDatabase() throws Exception {
        StaffMember saved = staffMemberRepository.save(getStaffMember());
        createFullWeekWorkingHours(saved.getId(), LocalTime.of(9, 0), LocalTime.of(18, 0));

        mockMvc.perform(put(BASE_URL + "/{id}/working-hours", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                fullWeekRequest(LocalTime.of(10, 30), LocalTime.of(16, 45))
                        )))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$[0].starts_at").value("10:30"))
                .andExpect(jsonPath("$[0].ends_at").value("16:45"))
                .andExpect(jsonPath("$[0].is_available").value(true));

        List<StaffWorkingHours> persisted = staffWorkingHoursRepository
                .findAllByStaffMemberIdOrderByDayOfWeekAsc(saved.getId());
        StaffWorkingHours monday = findPersistedDay(persisted, DayOfWeek.MONDAY);
        StaffWorkingHours saturday = findPersistedDay(persisted, DayOfWeek.SATURDAY);

        assertThat(persisted).hasSize(7);
        assertThat(monday.getStartsAt()).isEqualTo(LocalTime.of(10, 30));
        assertThat(monday.getEndsAt()).isEqualTo(LocalTime.of(16, 45));
        assertThat(saturday.isAvailable()).isFalse();
        assertThat(saturday.getStartsAt()).isNull();
        assertThat(saturday.getEndsAt()).isNull();
    }

    @Test
    void replaceWorkingHours_whenWeekIsIncomplete_returns400() throws Exception {
        StaffMember saved = staffMemberRepository.save(getStaffMember());
        createFullWeekWorkingHours(saved.getId(), LocalTime.of(9, 0), LocalTime.of(18, 0));

        mockMvc.perform(put(BASE_URL + "/{id}/working-hours", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                fullWeekRequest(LocalTime.of(10, 0), LocalTime.of(16, 0)).subList(0, 6)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Working hours must contain exactly 7 days"));
    }

    @Test
    void replaceWorkingHours_whenRangeIsInvalid_returns400() throws Exception {
        StaffMember saved = staffMemberRepository.save(getStaffMember());
        createFullWeekWorkingHours(saved.getId(), LocalTime.of(9, 0), LocalTime.of(18, 0));

        List<StaffWorkingHoursRequestDto> request = fullWeekRequest(LocalTime.of(10, 0), LocalTime.of(16, 0));
        request.get(0).setStartsAt(LocalTime.of(17, 0));
        request.get(0).setEndsAt(LocalTime.of(17, 0));

        mockMvc.perform(put(BASE_URL + "/{id}/working-hours", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Start time must be before end time for available days"));
    }

    @Test
    void workingHours_whenStaffIsOutsideBusinessScope_returns404() throws Exception {
        StaffMember staffMember = getStaffMember();
        staffMember.setBusinessId(2L);
        StaffMember saved = staffMemberRepository.save(staffMember);
        createFullWeekWorkingHours(saved.getId(), LocalTime.of(9, 0), LocalTime.of(18, 0));

        mockMvc.perform(get(BASE_URL + "/{id}/working-hours", saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: " + saved.getId()));

        mockMvc.perform(put(BASE_URL + "/{id}/working-hours", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                fullWeekRequest(LocalTime.of(10, 0), LocalTime.of(16, 0))
                        )))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: " + saved.getId()));

        List<StaffWorkingHours> unchanged = staffWorkingHoursRepository
                .findAllByStaffMemberIdOrderByDayOfWeekAsc(saved.getId());
        assertThat(unchanged).hasSize(7);
        assertThat(unchanged.get(0).getStartsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(unchanged.get(0).getEndsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void replaceWorkingHours_whenValidationFails_doesNotPartiallyReplaceWeek() throws Exception {
        StaffMember saved = staffMemberRepository.save(getStaffMember());
        createFullWeekWorkingHours(saved.getId(), LocalTime.of(9, 0), LocalTime.of(18, 0));

        List<StaffWorkingHoursRequestDto> request = fullWeekRequest(LocalTime.of(10, 0), LocalTime.of(16, 0));
        request.get(2).setStartsAt(null);

        mockMvc.perform(put(BASE_URL + "/{id}/working-hours", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Start time is required when the day is available"));

        List<StaffWorkingHours> unchanged = staffWorkingHoursRepository
                .findAllByStaffMemberIdOrderByDayOfWeekAsc(saved.getId());
        StaffWorkingHours monday = findPersistedDay(unchanged, DayOfWeek.MONDAY);
        StaffWorkingHours wednesday = findPersistedDay(unchanged, DayOfWeek.WEDNESDAY);

        assertThat(unchanged).hasSize(7);
        assertThat(monday.getStartsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(monday.getEndsAt()).isEqualTo(LocalTime.of(18, 0));
        assertThat(wednesday.getStartsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(wednesday.getEndsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    private void assertDeleteBlockedByFutureActiveAppointment(AppointmentStatus appointmentStatus) throws Exception {
        StaffMember savedStaffMember = staffMemberRepository.save(getStaffMember());
        Appointment savedAppointment = createAppointment(
                1L,
                savedStaffMember.getId(),
                appointmentStatus,
                LocalDateTime.now().plusDays(1)
        );

        mockMvc.perform(delete(BASE_URL + "/{id}", savedStaffMember.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Staff member cannot be deactivated because it has future active appointments"));

        StaffMember unchanged = staffMemberRepository.findById(savedStaffMember.getId()).orElseThrow();
        Appointment unchangedAppointment = appointmentRepository.findById(savedAppointment.getId()).orElseThrow();

        assertThat(unchanged.getStatus()).isEqualTo(StaffMemberStatus.ACTIVE);
        assertThat(unchangedAppointment.getStatus()).isEqualTo(appointmentStatus);
        assertThat(unchangedAppointment.getStaffMemberId()).isEqualTo(savedStaffMember.getId());
    }

    private void assertDeleteAllowedWithFutureNonBlockingAppointment(AppointmentStatus appointmentStatus) throws Exception {
        StaffMember savedStaffMember = staffMemberRepository.save(getStaffMember());
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1);
        Appointment savedAppointment = createAppointment(
                1L,
                savedStaffMember.getId(),
                appointmentStatus,
                startsAt
        );

        mockMvc.perform(delete(BASE_URL + "/{id}", savedStaffMember.getId()))
                .andExpect(status().isNoContent());

        StaffMember deactivated = staffMemberRepository.findById(savedStaffMember.getId()).orElseThrow();
        Appointment unchangedAppointment = appointmentRepository.findById(savedAppointment.getId()).orElseThrow();

        assertThat(deactivated.getStatus()).isEqualTo(StaffMemberStatus.INACTIVE);
        assertThat(unchangedAppointment.getStatus()).isEqualTo(appointmentStatus);
        assertThat(unchangedAppointment.getStartsAt()).isEqualTo(startsAt);
        assertThat(unchangedAppointment.getStaffMemberId()).isEqualTo(savedStaffMember.getId());
    }
}



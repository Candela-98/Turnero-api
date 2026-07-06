package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.User;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.model.enums.UserRole;
import com.turnero.api.repository.BusinessHoursRepository;
import com.turnero.api.repository.StaffMemberRepository;
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


import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
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
    BusinessHoursRepository businessHoursRepository;

    @Autowired
    UserRepository userRepository;

    private static final String BASE_URL = "/api/v1/staff-members";

    @BeforeEach
    void cleanDb() {
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
    void updateStaffMember_whenRequestIsValid_updatesStaffMember_andReturns204() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        StaffMember saved = staffMemberRepository.save(staffMember);

        StaffMemberRequestDto dto = getStaffMemberRequestDto();
        dto.setName("Matias Updated");
        dto.setRoleLabel("Lead barber");
        dto.setSpecialty("Barber Updated");
        dto.setAvatarUrl("https://example.com/avatar-updated.png");

        // When
        mockMvc.perform(put(BASE_URL + "/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        StaffMember updated = staffMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Matias Updated");
        assertThat(updated.getRoleLabel()).isEqualTo("Lead barber");
        assertThat(updated.getSpecialty()).isEqualTo("Barber Updated");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/avatar-updated.png");
    }

    @Test
    void updateStaffMember_whenNameIsBlank_returns400() throws Exception {
        // Given
        StaffMember saved = staffMemberRepository.save(getStaffMember());

        StaffMemberRequestDto dto = getStaffMemberRequestDto();
        dto.setName("");

        // When + Then
        mockMvc.perform(put(BASE_URL + "/{id}", saved.getId())
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
    void deleteStaffMember_whenStaffMemberExists_deletesStaffMember_andReturns204() throws Exception {
        // Given
        StaffMember staffMember = getStaffMember();
        StaffMember saved = staffMemberRepository.save(staffMember);
        Long id = saved.getId();

        // When
        mockMvc.perform(delete(BASE_URL + "/{id}", id))
                .andExpect(status().isNoContent());

        // Then
        assertThat(staffMemberRepository.existsById(saved.getId())).isFalse();
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
}



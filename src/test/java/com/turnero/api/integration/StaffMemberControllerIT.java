package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.mapper.StaffMemberMapper;
import com.turnero.api.model.StaffMember;
import com.turnero.api.repository.StaffMemberRepository;
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

    @BeforeEach
    void cleanDb() {
        staffMemberRepository.deleteAll();
    }

    private StaffMemberRequestDto getStaffMemberRequestDto() {
        return StaffMemberRequestDto.builder()
                .name("Matias")
                .specialty("Barber")
                .license("123456")
                .build();
    }

    private StaffMember getStaffMember() {
         return StaffMember.builder()
                 .name("Matias")
                 .specialty("Barber")
                 .license("123456")
                 .build();
    }

    @Test
    void saveStaffMember_whenRequestIsValid_persistsStaffMember_andReturns201() throws Exception {
        //Given
        StaffMemberRequestDto dto = getStaffMemberRequestDto();

        //When
        MvcResult result = mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        List<StaffMember> staffMembers = staffMemberRepository.findAll();

        assertThat(staffMembers).hasSize(1);
        StaffMember saved = staffMembers.get(0);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Matias");
        assertThat(saved.getSpecialty()).isEqualTo("Barber");
        assertThat(saved.getLicense()).isEqualTo("123456");

        String json = result.getResponse().getContentAsString();
        StaffMemberResponseDto response = objectMapper.readValue(json, StaffMemberResponseDto.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getName()).isEqualTo("Matias");
        assertThat(response.getSpecialty()).isEqualTo("Barber");
        assertThat(response.getLicense()).isEqualTo("123456");
    }

    @Test
    void saveStaffMember_whenNameIsBlank_returns400() throws Exception {
        // Given
        StaffMemberRequestDto dto = getStaffMemberRequestDto();
        dto.setName("");

        // When + Then
        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.name").value("The name of the staffmember is mandatory."));

        assertThat(staffMemberRepository.findAll()).isEmpty();
    }

    @Test
    void findStaffMember_whenExists_returns200AndStaffMember() throws Exception {
        // Given
        StaffMember saved = staffMemberRepository.save(getStaffMember());

        // When
        MvcResult result = mockMvc.perform(get("/api/staffmembers/{id}", saved.getId()))
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
        mockMvc.perform(get("/api/staffmembers/{id}", id))
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
        dto.setSpecialty("Barber Updated");
        dto.setLicense("654321");

        // When
        mockMvc.perform(put("/api/staffmembers/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        StaffMember updated = staffMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Matias Updated");
        assertThat(updated.getSpecialty()).isEqualTo("Barber Updated");
        assertThat(updated.getLicense()).isEqualTo("654321");
    }

    @Test
    void updateStaffMember_whenNameIsBlank_returns400() throws Exception {
        // Given
        StaffMember saved = staffMemberRepository.save(getStaffMember());

        StaffMemberRequestDto dto = getStaffMemberRequestDto();
        dto.setName("");

        // When + Then
        mockMvc.perform(put("/api/staffmembers/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.name").value("The name of the staffmember is mandatory."));
    }

    @Test
    void listStaffMembers_whenExist_returns200AndList() throws Exception {
        // Given
        staffMemberRepository.save(getStaffMember());

        StaffMember second = new StaffMember();
        second.setName("Maria");
        second.setSpecialty("Colorista");
        second.setLicense("87443");

        staffMemberRepository.save(second);

        // When
        MvcResult result = mockMvc.perform(get("/api/staffmembers"))
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
    void listStaffMembers_whenNoCustomersExist_returns200AndEmptyList() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/staffmembers")
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
        mockMvc.perform(delete("/api/staffmembers/{id}", id))
                .andExpect(status().isNoContent());

        // Then
        assertThat(staffMemberRepository.existsById(saved.getId())).isFalse();
    }

    @Test
    void deleteStaffMember_whenStaffMemberDoesNotExist_returns404() throws Exception {
        // Given
        Long id = 999L;

        // When + Then
        mockMvc.perform(delete("/api/staffmembers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));
    }
}

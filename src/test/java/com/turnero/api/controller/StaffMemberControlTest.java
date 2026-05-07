package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.StaffMemberMapper;

import com.turnero.api.model.StaffMember;
import com.turnero.api.service.StaffMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(StaffMemberController.class)
public class StaffMemberControlTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffMemberService staffService;
    @MockitoBean
    private StaffMemberMapper staffMapper;

    private StaffMemberRequestDto getStaffMemberDTO(Long id){
        return StaffMemberRequestDto.builder()
                .name("Daniel Leguizamon")
                .specialty("Barber")
                .license("A12322")
                .build();
    }

    private StaffMember getStaffMemberEntity(Long id){
        return  StaffMember.builder()
                .id(id)
                .name("Daniel Leguizamon")
                .specialty("Barber")
                .license("A12322")
                .build();
    }

    private StaffMemberResponseDto getStaffMemberResponseDTO(Long id){
        return StaffMemberResponseDto.builder()
                .id(id)
                .name("Daniel Leguizamon")
                .specialty("Barber")
                .license("A12322")
                .build();
    }

    @Test
    void saveStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        Long id = 1L;
        var dto = getStaffMemberDTO(id);
        var entity = getStaffMemberEntity(id);
        var responseDto = getStaffMemberResponseDTO(id);

        given(staffMapper.toEntity(any(StaffMemberRequestDto.class))).willReturn(entity);
<<<<<<< refactor/serviceOffering-api-contract
        given(staffMapper.toResponseDto(any(StaffMember.class))).willReturn(responseDto);
=======
            given(staffMapper.toResponseDto(any(StaffMember.class))).willReturn(responseDto);
>>>>>>> refactor/customer-api-contract

        // When
        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Assert
        then(staffMapper).should().toEntity(any(StaffMemberRequestDto.class));
        then(staffService).should().saveStaffMember(entity);
        then(staffMapper).should().toResponseDto(entity);
    }

    @Test
    void saveStaffMember_whenNameIsBlank_shouldReturn400() throws Exception {
        // Given
        Long id = 1L;
        StaffMemberRequestDto dto = getStaffMemberDTO(id);
        dto.setName("");

        // When
        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.name").exists());

        // Then
        then(staffMapper).shouldHaveNoInteractions();
        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void saveStaffMember_whenLicenseIsBlank_shouldReturn400() throws Exception {
        // Given
        Long id = 1L;
        StaffMemberRequestDto dto = getStaffMemberDTO(id);
        dto.setLicense("");

        // When
        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.license").exists());

        // Then
        then(staffMapper).shouldHaveNoInteractions();
        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void listStaffMembers_ok_shouldReturn200_andCallService() throws Exception {
        //Given
        Long id = 1L;
        var staffMember1 = getStaffMemberEntity(id);
        var staffMember2 = getStaffMemberEntity(2L);

        given(staffService.findAllStaffMember())
                .willReturn(List.of(staffMember1, staffMember2));
        given(staffMapper.toResponseDtoList(List.of(staffMember1, staffMember2)))
                .willReturn(List.of(getStaffMemberResponseDTO(id), getStaffMemberResponseDTO(2L)));

        //When + Then
        mockMvc.perform(get("/api/staffmembers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        then(staffService).should().findAllStaffMember();
        then(staffMapper).should().toResponseDtoList(List.of(staffMember1, staffMember2));
    }

    @Test
    void findStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        Long id = 1L;
        var staffMember = getStaffMemberEntity(id);
        var responseDto = getStaffMemberResponseDTO(id);

        given (staffService.findStaffMember(id)).willReturn(staffMember);
        given(staffMapper.toResponseDto(staffMember)).willReturn(responseDto);

        // When + Then
        mockMvc.perform(get("/api/staffmembers/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Daniel Leguizamon"))
                .andExpect(jsonPath("$.specialty").value("Barber"))
                .andExpect(jsonPath("$.license").value("A12322"));

        then(staffService).should().findStaffMember(1L);
        then(staffMapper).should().toResponseDto(staffMember);
    }

    @Test
    void findStaffMember_withNonExistingId_shouldReturn404() throws Exception {
        // Given
        given(staffService.findStaffMember(999L)).willThrow(new ResourceNotFoundException("Staff member not found"));

        // When + Then
        mockMvc.perform(get("/api/staffmembers/999")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staff member not found"));

        then(staffService).should().findStaffMember(999L);
        then(staffMapper).shouldHaveNoInteractions();
    }

    @Test
    void updateStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        Long id = 1L;
        var dto = getStaffMemberDTO(id);
        var entity = getStaffMemberEntity(id);

        given(staffMapper.toEntity(any(StaffMemberRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(put("/api/staffmembers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        //Then
        then(staffMapper).should().toEntity(any(StaffMemberRequestDto.class));
        then(staffService).should().updateStaffMember(entity, 1L);
    }

    @Test
    void updateStaffMember_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        Long id = 1L;
        var dto = getStaffMemberDTO(id);
        dto.setName("");

        // When + Then
        mockMvc.perform(put("/api/staffmembers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.name").exists());

        then(staffMapper).shouldHaveNoInteractions();
        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void deleteStaffMember_ok_shouldReturn200_andCallService() throws Exception {
        //When + Then
        mockMvc.perform(delete("/api/staffmembers/1"))
                .andExpect(status().isNoContent());

        then(staffService).should().deleteStaffMember(1L);
    }

    @Test
    void deleteStaffMember_withNonExistingId_shouldReturn404() throws Exception {
        // Given
        Long id = 999L;

        willThrow(new ResourceNotFoundException("Staff member not found"))
                .given(staffService).deleteStaffMember(id);

        // When + Then
        mockMvc.perform(delete("/api/staffmembers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staff member not found"));

        then(staffService).should().deleteStaffMember(999L);
    }
}


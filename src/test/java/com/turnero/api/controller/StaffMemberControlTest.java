package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.mapper.StaffMemberMapper;

import com.turnero.api.model.StaffMember;
import com.turnero.api.service.StaffMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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

    private StaffMemberRequestDto validDto(){
        StaffMemberRequestDto dto = new StaffMemberRequestDto();
        dto.setStaffMemberId(1L);
        dto.setNameStaffMember("Daniel Leguizamon");
        dto.setSpecialty("Barber");
        dto.setLicense("A12322");
        return dto;
    }

    private StaffMember getStaffMemberEntity(){
        StaffMember prof = new StaffMember();
        prof.setId(1L);
        prof.setName("Daniel Leguizamon");
        prof.setSpecialty("Barber");
        prof.setLicense("A12322");
        return prof;
    }

    private StaffMember staffMemberWithId(long id){
        StaffMember prof = new StaffMember();
        prof.setId(id);
        prof.setName("Daniel Leguizamon");
        prof.setSpecialty("Barber");
        prof.setLicense("A12322");
        return prof;
    }

    void saveStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        StaffMemberRequestDto dto = validDto();
        StaffMember entity = new StaffMember();
        given(staffMapper.toEntity(any(StaffMemberRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // Assert
        then(staffMapper).should().toEntity(any(StaffMemberRequestDto.class));
        then(staffService).should().saveStaffMember(entity);
    }

    @Test
    void saveStaffMember_withInvalidDto_shouldReturn400() throws Exception {
        // Given
        StaffMemberRequestDto dto = validDto();
        dto.setNameStaffMember(null);

        // When
        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // Then
        then(staffMapper).shouldHaveNoInteractions();
        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void listStaffMembers_ok_shouldReturn200_andCallService() throws Exception {
        //Given
        given(staffService.findAllStaffMember())
                .willReturn(java.util.List.of(staffMemberWithId(1L), staffMemberWithId(2L)));

        //When + Then
        mockMvc.perform(get("/api/staffmembers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        then(staffService).should().findAllStaffMember();
    }

    @Test
    void findStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        given (staffService.findStaffMember(1L)).willReturn(staffMemberWithId(1L));

        // When + Then
        mockMvc.perform(get("/api/staffmembers/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Daniel Leguizamon"))
                .andExpect(jsonPath("$.specialty").value("Barber"))
                .andExpect(jsonPath("$.license").value("A12322"));
        then(staffService).should().findStaffMember(1L);
    }

    @Test
    void findStaffMember_withNonExistingId_shouldReturn404() throws Exception {
        // Given
        given(staffService.findStaffMember(999L)).willThrow(new ResponseStatusException(NOT_FOUND, "Staff member not found"));

        // When + Then
        mockMvc.perform(get("/api/staffmembers/999"))
                .andExpect(status().isNotFound());

        then(staffService).should().findStaffMember(999L);
    }

    @Test
    void updateStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        StaffMemberRequestDto dto = validDto();
        StaffMember entity = new StaffMember();
        given(staffMapper.toEntity(any(StaffMemberRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(put("/api/staffmembers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        //Then
        then(staffMapper).should().toEntity(any(StaffMemberRequestDto.class));
        then(staffService).should().updateStaffMember(entity, 1L);
    }

    @Test
    void updateStaffMember_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        StaffMemberRequestDto dto = validDto();
        dto.setNameStaffMember(null);

        // When + Then
        mockMvc.perform(put("/api/staffmembers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

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
}


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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    private StaffMember staffMemberWithId(long id){
        StaffMember prof = new StaffMember();
        prof.setId(id);
        prof.setName("Daniel Leguizamon");
        prof.setSpecialty("Barbero");
        prof.setLicense("A12322");
        return prof;
    }

    void saveStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        StaffMemberRequestDto dto = validDto();
        StaffMember entity = new StaffMember();
        when(staffMapper.toEntity(any(StaffMemberRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(staffMapper).toEntity(any(StaffMemberRequestDto.class));
        verify(staffService).saveStaffMember(entity);
    }

    @Test
    void saveStaffMember_withInvalidDto_shouldReturn400() throws Exception {
        StaffMemberRequestDto dto = validDto();
        dto.setNameStaffMember("");

        StaffMember entity = new StaffMember();
        when(staffMapper.toEntity(any(StaffMemberRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/staffmembers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(staffMapper).toEntity(any(StaffMemberRequestDto.class));
        verify(staffService).saveStaffMember(entity);
    }

    @Test
    void listStaffMembers_ok_shouldReturn200_andCallService() throws Exception {
        when(staffService.findAllStaffMember())
                .thenReturn(java.util.List.of(staffMemberWithId(1L), staffMemberWithId(2L)));

        mockMvc.perform(get("/api/staffmembers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(staffService).findAllStaffMember();
    }

    @Test
    void findStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        when (staffService.findStaffMember(1L)).thenReturn(staffMemberWithId(1L));

        mockMvc.perform(get("/api/staffmembers/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Daniel Leguizamon"))
                .andExpect(jsonPath("$.specialty").value("Barber"))
                .andExpect(jsonPath("$.license").value("A12322"));
        verify(staffService).findStaffMember(1L);
    }

    @Test
    void findStaffMember_withNonExistingId_shouldReturn404() throws Exception {
        when(staffService.findStaffMember(999L)).thenReturn(null);
        mockMvc.perform(get("/api/staffmembers/999"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(staffService).findStaffMember(999L);
    }

    @Test
    void updateStaffMember_ok_shouldReturn200_andCallService() throws Exception{
        StaffMemberRequestDto dto = validDto();
        StaffMember entity = new StaffMember();
        when(staffMapper.toEntity(any(StaffMemberRequestDto.class))).thenReturn(entity);
        mockMvc.perform(put("/api/staffmembers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateStaffMember_withInvalidDto_shouldReturn400() throws Exception{
        StaffMemberRequestDto dto = validDto();
        dto.setNameStaffMember("");

        StaffMember entity = new StaffMember();
        when(staffMapper.toEntity(any(StaffMemberRequestDto.class))).thenReturn(entity);

        mockMvc.perform(put("/api/staffmembers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(staffMapper).toEntity(any(StaffMemberRequestDto.class));
        verify(staffService).updateStaffMember(entity, 1L);
    }

    @Test
    void deleteStaffMember_ok_shouldReturn200_andCallService() throws Exception {
        mockMvc.perform(delete("/api/staffmembers/1"))
                .andExpect(status().isOk());

        verify(staffService).deleteStaffMember(1L);
    }
}


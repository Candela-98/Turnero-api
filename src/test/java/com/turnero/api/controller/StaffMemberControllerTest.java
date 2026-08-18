package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.StaffMemberRequestDto;
import com.turnero.api.dto.StaffMemberResponseDto;
import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.dto.StaffWorkingHoursRequestDto;
import com.turnero.api.dto.StaffWorkingHoursResponseDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.StaffMemberMapper;

import com.turnero.api.model.StaffMember;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.service.StaffMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(StaffMemberController.class)
public class StaffMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffMemberService staffService;
    @MockitoBean
    private StaffMemberMapper staffMapper;

    private static final String BASE_URL = "/api/v1/staff-members";

    private StaffMemberRequestDto getStaffMemberDTO(Long id){
        return StaffMemberRequestDto.builder()
                .userId(20L)
                .name("Daniel Leguizamon")
                .roleLabel("Senior barber")
                .specialty("Barber")
                .avatarUrl("https://example.com/avatar.png")
                .build();
    }

    private StaffMember getStaffMemberEntity(Long id){
        return  StaffMember.builder()
                .id(id)
                .businessId(10L)
                .userId(20L)
                .name("Daniel Leguizamon")
                .roleLabel("Senior barber")
                .specialty("Barber")
                .avatarUrl("https://example.com/avatar.png")
                .status(StaffMemberStatus.ACTIVE)
                .build();
    }

    private StaffMemberResponseDto getStaffMemberResponseDTO(Long id){
        return StaffMemberResponseDto.builder()
                .id(id)
                .businessId(10L)
                .userId(20L)
                .name("Daniel Leguizamon")
                .roleLabel("Senior barber")
                .specialty("Barber")
                .avatarUrl("https://example.com/avatar.png")
                .status(StaffMemberStatus.ACTIVE)
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
        given(staffMapper.toResponseDto(any(StaffMember.class))).willReturn(responseDto);

        // When
        mockMvc.perform(post(BASE_URL)
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
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());
        // Then
        then(staffMapper).shouldHaveNoInteractions();
        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void saveStaffMember_whenSpecialtyIsBlank_shouldReturn400() throws Exception {
        // Given
        Long id = 1L;
        StaffMemberRequestDto dto = getStaffMemberDTO(id);
        dto.setSpecialty("");

        // When
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("specialty"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());

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
        mockMvc.perform(get(BASE_URL))
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
        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Daniel Leguizamon"))
                .andExpect(jsonPath("$.specialty").value("Barber"))
                .andExpect(jsonPath("$.role_label").value("Senior barber"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        then(staffService).should().findStaffMember(1L);
        then(staffMapper).should().toResponseDto(staffMember);
    }

    @Test
    void findStaffMember_withNonExistingId_shouldReturn404() throws Exception {
        // Given
        given(staffService.findStaffMember(999L)).willThrow(new ResourceNotFoundException("Staff member not found"));

        // When + Then
        mockMvc.perform(get(BASE_URL + "/999")
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
        var dto = StaffMemberUpdateRequestDto.builder()
                .name("Daniel Updated")
                .roleLabel("Lead barber")
                .specialty("Barber Updated")
                .avatarUrl("https://example.com/avatar-updated.png")
                .status(StaffMemberStatus.INACTIVE)
                .build();
        var entity = getStaffMemberEntity(id);
        entity.setName("Daniel Updated");
        entity.setRoleLabel("Lead barber");
        entity.setSpecialty("Barber Updated");
        entity.setAvatarUrl("https://example.com/avatar-updated.png");
        entity.setStatus(StaffMemberStatus.INACTIVE);
        var responseDto = StaffMemberResponseDto.builder()
                .id(id)
                .businessId(10L)
                .userId(20L)
                .name("Daniel Updated")
                .roleLabel("Lead barber")
                .specialty("Barber Updated")
                .avatarUrl("https://example.com/avatar-updated.png")
                .status(StaffMemberStatus.INACTIVE)
                .build();

        given(staffService.updateStaffMember(any(StaffMemberUpdateRequestDto.class), eq(id))).willReturn(entity);
        given(staffMapper.toResponseDto(entity)).willReturn(responseDto);

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Daniel Updated"))
                .andExpect(jsonPath("$.role_label").value("Lead barber"))
                .andExpect(jsonPath("$.specialty").value("Barber Updated"))
                .andExpect(jsonPath("$.avatar_url").value("https://example.com/avatar-updated.png"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        then(staffService).should().updateStaffMember(any(StaffMemberUpdateRequestDto.class), eq(id));
        then(staffMapper).should().toResponseDto(entity);
    }

    @Test
    void updateStaffMember_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        Long id = 1L;
        var dto = StaffMemberUpdateRequestDto.builder()
                .name("")
                .build();

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL + "/1"))
                .andExpect(jsonPath("$.timestamp").exists());

        then(staffMapper).shouldHaveNoInteractions();
        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void updateStaffMember_withNonExistingId_shouldReturn404() throws Exception {
        // Given
        var dto = StaffMemberUpdateRequestDto.builder()
                .name("Daniel Updated")
                .build();

        given(staffService.updateStaffMember(any(StaffMemberUpdateRequestDto.class), eq(999L)))
                .willThrow(new ResourceNotFoundException("Staffmember not found with ID: 999"));

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));

        then(staffService).should().updateStaffMember(any(StaffMemberUpdateRequestDto.class), eq(999L));
        then(staffMapper).shouldHaveNoInteractions();
    }

    @Test
    void deleteStaffMember_ok_shouldReturn200_andCallService() throws Exception {
        //When + Then
        mockMvc.perform(delete(BASE_URL + "/1"))
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
        mockMvc.perform(delete(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staff member not found"));

        then(staffService).should().deleteStaffMember(999L);
    }

    @Test
    void getWorkingHours_ok_shouldReturn200_andCallService() throws Exception {
        Long id = 1L;
        given(staffService.getWorkingHours(id)).willReturn(List.of(
                workingHoursResponse(DayOfWeek.MONDAY),
                workingHoursResponse(DayOfWeek.TUESDAY)
        ));

        mockMvc.perform(get(BASE_URL + "/{id}/working-hours", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$[0].starts_at").value("09:00"))
                .andExpect(jsonPath("$[0].ends_at").value("17:00"))
                .andExpect(jsonPath("$[0].is_available").value(true));

        then(staffService).should().getWorkingHours(id);
    }

    @Test
    void replaceWorkingHours_ok_shouldReturn200_andSevenWorkingHours() throws Exception {
        Long id = 1L;
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();

        given(staffService.replaceWorkingHours(eq(id), anyList())).willReturn(fullWeekResponse());

        mockMvc.perform(put(BASE_URL + "/{id}/working-hours", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$[0].starts_at").value("09:00"))
                .andExpect(jsonPath("$[0].ends_at").value("17:00"))
                .andExpect(jsonPath("$[0].is_available").value(true));

        then(staffService).should().replaceWorkingHours(eq(id), anyList());
    }

    @Test
    void replaceWorkingHours_whenDayOfWeekIsMissing_shouldReturn400() throws Exception {
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();
        request.get(0).setDayOfWeek(null);

        mockMvc.perform(put(BASE_URL + "/1/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void replaceWorkingHours_whenIsAvailableIsMissing_shouldReturn400() throws Exception {
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();
        request.get(0).setIsAvailable(null);

        mockMvc.perform(put(BASE_URL + "/1/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        then(staffService).shouldHaveNoInteractions();
    }

    @Test
    void replaceWorkingHours_whenBusinessValidationFails_shouldReturn400() throws Exception {
        given(staffService.replaceWorkingHours(eq(1L), anyList()))
                .willThrow(new IllegalArgumentException("Working hours must contain exactly 7 days"));

        mockMvc.perform(put(BASE_URL + "/1/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullWeekRequest().subList(0, 6))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Working hours must contain exactly 7 days"));

        then(staffService).should().replaceWorkingHours(eq(1L), anyList());
    }

    @Test
    void getWorkingHours_whenOutsideScope_shouldReturn404() throws Exception {
        given(staffService.getWorkingHours(999L))
                .willThrow(new ResourceNotFoundException("Staffmember not found with ID: 999"));

        mockMvc.perform(get(BASE_URL + "/999/working-hours"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));

        then(staffService).should().getWorkingHours(999L);
    }

    @Test
    void replaceWorkingHours_whenOutsideScope_shouldReturn404() throws Exception {
        given(staffService.replaceWorkingHours(eq(999L), anyList()))
                .willThrow(new ResourceNotFoundException("Staffmember not found with ID: 999"));

        mockMvc.perform(put(BASE_URL + "/999/working-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullWeekRequest())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staffmember not found with ID: 999"));

        then(staffService).should().replaceWorkingHours(eq(999L), anyList());
    }

    private List<StaffWorkingHoursRequestDto> fullWeekRequest() {
        return List.of(
                workingHoursRequest(DayOfWeek.MONDAY),
                workingHoursRequest(DayOfWeek.TUESDAY),
                workingHoursRequest(DayOfWeek.WEDNESDAY),
                workingHoursRequest(DayOfWeek.THURSDAY),
                workingHoursRequest(DayOfWeek.FRIDAY),
                workingHoursRequest(DayOfWeek.SATURDAY),
                workingHoursRequest(DayOfWeek.SUNDAY)
        );
    }

    private List<StaffWorkingHoursResponseDto> fullWeekResponse() {
        return List.of(
                workingHoursResponse(DayOfWeek.MONDAY),
                workingHoursResponse(DayOfWeek.TUESDAY),
                workingHoursResponse(DayOfWeek.WEDNESDAY),
                workingHoursResponse(DayOfWeek.THURSDAY),
                workingHoursResponse(DayOfWeek.FRIDAY),
                workingHoursResponse(DayOfWeek.SATURDAY),
                workingHoursResponse(DayOfWeek.SUNDAY)
        );
    }

    private StaffWorkingHoursRequestDto workingHoursRequest(DayOfWeek dayOfWeek) {
        return StaffWorkingHoursRequestDto.builder()
                .dayOfWeek(dayOfWeek)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(17, 0))
                .isAvailable(true)
                .build();
    }

    private StaffWorkingHoursResponseDto workingHoursResponse(DayOfWeek dayOfWeek) {
        return StaffWorkingHoursResponseDto.builder()
                .dayOfWeek(dayOfWeek)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(17, 0))
                .isAvailable(true)
                .build();
    }
}




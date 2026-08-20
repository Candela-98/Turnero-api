package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.dto.StaffWorkingHoursRequestDto;
import com.turnero.api.dto.StaffWorkingHoursResponseDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.StaffWorkingHours;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.BusinessHoursRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffWorkingHoursRepository;
import com.turnero.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaffMemberServiceImplTest {
    @Mock
    private StaffMemberRepository staffMemberRepository;

    @InjectMocks
    private StaffMemberServiceImpl staffMemberService;

    @Mock
    private CurrentBusinessContext currentBusinessContext;

    @Mock
    private BusinessHoursRepository businessHoursRepository;

    @Mock
    private StaffWorkingHoursRepository staffWorkingHoursRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Test
    void saveStaffMember_shouldSaveAndReturnStaffMember() {
        StaffMember staffMember = new StaffMember();
        staffMember.setName("Juan");
        staffMember.setSpecialty("Barber");
        staffMember.setUserId(20L);

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(1L)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(18, 0))
                .isClosed(false)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(businessHoursRepository.findAllByBusinessId(1L)).thenReturn(List.of(businessHours));
        when(staffMemberRepository.save(staffMember)).thenReturn(staffMember);
        when(userRepository.existsByIdAndBusinessId(20L, 1L)).thenReturn(true);

        StaffMember result = staffMemberService.saveStaffMember(staffMember);

        assertNotNull(result);
        assertEquals("Juan", result.getName());
        assertEquals(1L, result.getBusinessId());
        assertEquals(StaffMemberStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        verify(staffMemberRepository, times(1)).save(staffMember);
        verify(staffWorkingHoursRepository, times(1)).saveAll(anyList());
    }

    @Test
    void saveStaffMember_whenUserDoesNotBelongToCurrentBusiness_throwsException() {
        StaffMember staffMember = new StaffMember();
        staffMember.setUserId(20L);
        staffMember.setName("Juan");
        staffMember.setSpecialty("Barber");

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(userRepository.existsByIdAndBusinessId(20L, 1L)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> staffMemberService.saveStaffMember(staffMember)
        );

        assertEquals("User not found for current business", exception.getMessage());

        verify(staffMemberRepository, never()).save(any());
        verify(staffWorkingHoursRepository, never()).saveAll(anyList());
    }

    @Test
    void findStaffMember_whenExists_returnsStaffMember() {
        Long id = 1L;
        Long businessId = 1L;
        StaffMember staffMember = new StaffMember();
        staffMember.setId(id);
        staffMember.setBusinessId(businessId);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(staffMember));

        StaffMember result = staffMemberService.findStaffMember(id);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(businessId, result.getBusinessId());
        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(staffMemberRepository, times(1)).findByIdAndBusinessId(id, businessId);
    }

    @Test
    void findStaffMember_whenNotExists_throwsException() {
        Long id = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> staffMemberService.findStaffMember(id));

        assertEquals("Staffmember not found with ID: " + id, exception.getMessage());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(staffMemberRepository, times(1)).findByIdAndBusinessId(id, businessId);
    }

    @Test
    void listStaffMembers_shouldReturnList() {
        StaffMember p1 = new StaffMember();
        p1.setBusinessId(1L);

        StaffMember p2 = new StaffMember();
        p2.setBusinessId(1L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(staffMemberRepository.findAllByBusinessId(1L)).thenReturn(List.of(p1, p2));

        List<StaffMember> list = staffMemberService.findAllStaffMember();

        assertEquals(2, list.size());

        verify(staffMemberRepository, times(1)).findAllByBusinessId(1L);
        verify(staffMemberRepository, never()).findAll();
    }

    @Test
    void updateStaffMember_whenExists_updatesAndSaves() {
        Long id = 1L;
        Long businessId = 1L;

        StaffMember current = new StaffMember();
        current.setId(id);
        current.setBusinessId(businessId);
        current.setUserId(20L);
        current.setName("Carlos");
        current.setRoleLabel("Junior barber");
        current.setSpecialty("Barber");
        current.setAvatarUrl("https://example.com/avatar.png");
        current.setStatus(StaffMemberStatus.ACTIVE);

        StaffMemberUpdateRequestDto updatedStaff = StaffMemberUpdateRequestDto.builder()
                .name("Juan Carlos")
                .roleLabel("Senior barber")
                .specialty("Barber plus")
                .avatarUrl("https://example.com/avatar-updated.png")
                .status(StaffMemberStatus.INACTIVE)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(current));
        when(staffMemberRepository.save(current)).thenReturn(current);

        StaffMember result = staffMemberService.updateStaffMember(updatedStaff, id);

        verify(staffMemberRepository, times(1)).save(current);
        assertEquals("Juan Carlos", current.getName());
        assertEquals("Senior barber", current.getRoleLabel());
        assertEquals("Barber plus", current.getSpecialty());
        assertEquals("https://example.com/avatar-updated.png", current.getAvatarUrl());
        assertEquals(StaffMemberStatus.INACTIVE, current.getStatus());
        assertEquals(businessId, current.getBusinessId());
        assertEquals(20L, current.getUserId());
        assertSame(current, result);
    }

    @Test
    void updateStaffMember_whenPartialRequest_updatesOnlyProvidedFields() {
        Long id = 1L;
        Long businessId = 1L;

        StaffMember current = new StaffMember();
        current.setId(id);
        current.setBusinessId(businessId);
        current.setUserId(20L);
        current.setName("Carlos");
        current.setRoleLabel("Junior barber");
        current.setSpecialty("Barber");
        current.setAvatarUrl("https://example.com/avatar.png");
        current.setStatus(StaffMemberStatus.ACTIVE);

        StaffMemberUpdateRequestDto updateRequest = StaffMemberUpdateRequestDto.builder()
                .roleLabel("Lead barber")
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(current));
        when(staffMemberRepository.save(current)).thenReturn(current);

        staffMemberService.updateStaffMember(updateRequest, id);

        assertEquals("Carlos", current.getName());
        assertEquals("Lead barber", current.getRoleLabel());
        assertEquals("Barber", current.getSpecialty());
        assertEquals("https://example.com/avatar.png", current.getAvatarUrl());
        assertEquals(StaffMemberStatus.ACTIVE, current.getStatus());
    }

    @Test
    void updateStaffMember_whenDoesNotExist_throwsException_andDoesNotSave() {
        Long id = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        StaffMemberUpdateRequestDto updateStaff = StaffMemberUpdateRequestDto.builder()
                .name("New")
                .build();

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> staffMemberService.updateStaffMember(updateStaff, id));

        assertEquals("Staffmember not found with ID: " + id, exception.getMessage());

        verify(staffMemberRepository, never()).save(any());
    }

    @Test
    void deleteStaffMember_whenExists_setsStatusInactiveAndDoesNotDelete() {
        Long id = 1L;
        Long businessId = 1L;
        StaffMember staffMember = StaffMember.builder()
                .id(id)
                .businessId(businessId)
                .status(StaffMemberStatus.ACTIVE)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(staffMember));
        when(appointmentRepository.existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
                eq(businessId),
                eq(id),
                eq(List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)),
                any(LocalDateTime.class)
        )).thenReturn(false);
        when(staffMemberRepository.save(staffMember)).thenReturn(staffMember);

        staffMemberService.deleteStaffMember(id);

        assertEquals(StaffMemberStatus.INACTIVE, staffMember.getStatus());
        assertNotNull(staffMember.getUpdatedAt());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(staffMemberRepository).findByIdAndBusinessId(id, businessId);
        verify(appointmentRepository).existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
                eq(businessId),
                eq(id),
                eq(List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)),
                any(LocalDateTime.class)
        );
        verify(staffMemberRepository).save(staffMember);
        verify(staffMemberRepository, never()).deleteById(anyLong());
        verify(staffMemberRepository, never()).delete(any(StaffMember.class));
    }

    @Test
    void deleteStaffMember_whenDoesNotExist_throwsException() {
        Long id = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> staffMemberService.deleteStaffMember(id));

        assertEquals("Staffmember not found with ID: " + id, exception.getMessage());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(staffMemberRepository).findByIdAndBusinessId(id, businessId);
        verify(appointmentRepository, never()).existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
                anyLong(),
                anyLong(),
                anyList(),
                any(LocalDateTime.class)
        );
        verify(staffMemberRepository, never()).save(any());
        verify(staffMemberRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteStaffMember_whenOutsideBusinessScope_throwsExceptionAndDoesNotSave() {
        Long id = 1L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> staffMemberService.deleteStaffMember(id));

        verify(staffMemberRepository).findByIdAndBusinessId(id, businessId);
        verify(appointmentRepository, never()).existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
                anyLong(),
                anyLong(),
                anyList(),
                any(LocalDateTime.class)
        );
        verify(staffMemberRepository, never()).save(any());
        verify(staffMemberRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteStaffMember_whenFuturePendingAppointmentExists_throwsConflictAndDoesNotSave() {
        assertDeleteBlockedByFutureAppointment(AppointmentStatus.PENDING);
    }

    @Test
    void deleteStaffMember_whenFutureConfirmedAppointmentExists_throwsConflictAndDoesNotSave() {
        assertDeleteBlockedByFutureAppointment(AppointmentStatus.CONFIRMED);
    }

    @Test
    void getWorkingHours_whenStaffExists_returnsWorkingHours() {
        Long staffMemberId = 1L;
        Long businessId = 1L;
        StaffMember staffMember = StaffMember.builder()
                .id(staffMemberId)
                .businessId(businessId)
                .build();
        StaffWorkingHours monday = workingHoursEntity(staffMemberId, DayOfWeek.MONDAY, true);
        StaffWorkingHours tuesday = workingHoursEntity(staffMemberId, DayOfWeek.TUESDAY, false);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(staffMemberId, businessId))
                .thenReturn(Optional.of(staffMember));
        when(staffWorkingHoursRepository.findAllByStaffMemberIdOrderByDayOfWeekAsc(staffMemberId))
                .thenReturn(List.of(monday, tuesday));

        List<StaffWorkingHoursResponseDto> response = staffMemberService.getWorkingHours(staffMemberId);

        assertEquals(2, response.size());
        assertEquals(DayOfWeek.MONDAY, response.get(0).getDayOfWeek());
        assertEquals(LocalTime.of(9, 0), response.get(0).getStartsAt());
        assertEquals(LocalTime.of(18, 0), response.get(0).getEndsAt());
        assertTrue(response.get(0).isAvailable());
        assertEquals(DayOfWeek.TUESDAY, response.get(1).getDayOfWeek());
        assertFalse(response.get(1).isAvailable());

        verify(staffMemberRepository).findByIdAndBusinessId(staffMemberId, businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberIdOrderByDayOfWeekAsc(staffMemberId);
    }

    @Test
    void getWorkingHours_whenStaffDoesNotExistOrIsOutsideScope_throwsException() {
        Long staffMemberId = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(staffMemberId, businessId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> staffMemberService.getWorkingHours(staffMemberId)
        );

        assertEquals("Staffmember not found with ID: " + staffMemberId, exception.getMessage());
        verify(staffWorkingHoursRepository, never()).findAllByStaffMemberIdOrderByDayOfWeekAsc(anyLong());
    }

    @Test
    void replaceWorkingHours_whenRequestIsValid_replacesAllSevenDays() {
        Long staffMemberId = 1L;
        Long businessId = 1L;
        StaffMember staffMember = StaffMember.builder()
                .id(staffMemberId)
                .businessId(businessId)
                .build();
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(staffMemberId, businessId))
                .thenReturn(Optional.of(staffMember));
        when(staffWorkingHoursRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StaffWorkingHoursResponseDto> response = staffMemberService.replaceWorkingHours(staffMemberId, request);

        assertEquals(7, response.size());
        assertEquals(DayOfWeek.MONDAY, response.get(0).getDayOfWeek());
        assertEquals(LocalTime.of(9, 0), response.get(0).getStartsAt());
        assertEquals(LocalTime.of(17, 0), response.get(0).getEndsAt());
        assertTrue(response.get(0).isAvailable());

        verify(staffWorkingHoursRepository).deleteAllByStaffMemberId(staffMemberId);
        verify(staffWorkingHoursRepository).saveAll(argThat(saved -> {
            List<StaffWorkingHours> savedHours = (List<StaffWorkingHours>) saved;
            return savedHours.size() == 7
                    && savedHours.stream().allMatch(hour -> staffMemberId.equals(hour.getStaffMemberId()));
        }));
    }

    @Test
    void replaceWorkingHours_whenWeekHasLessThanSevenDays_throwsExceptionAndDoesNotModifyHours() {
        assertInvalidWorkingHoursDoesNotModify(fullWeekRequest().subList(0, 6));
    }

    @Test
    void replaceWorkingHours_whenWeekHasDuplicateDays_throwsExceptionAndDoesNotModifyHours() {
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();
        request.get(6).setDayOfWeek(DayOfWeek.MONDAY);

        assertInvalidWorkingHoursDoesNotModify(request);
    }

    @Test
    void replaceWorkingHours_whenAvailableDayHasNoStartTime_throwsExceptionAndDoesNotModifyHours() {
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();
        request.get(0).setStartsAt(null);

        assertInvalidWorkingHoursDoesNotModify(request);
    }

    @Test
    void replaceWorkingHours_whenAvailableDayHasNoEndTime_throwsExceptionAndDoesNotModifyHours() {
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();
        request.get(0).setEndsAt(null);

        assertInvalidWorkingHoursDoesNotModify(request);
    }

    @Test
    void replaceWorkingHours_whenStartTimeIsNotBeforeEndTime_throwsExceptionAndDoesNotModifyHours() {
        List<StaffWorkingHoursRequestDto> request = fullWeekRequest();
        request.get(0).setStartsAt(LocalTime.of(18, 0));
        request.get(0).setEndsAt(LocalTime.of(18, 0));

        assertInvalidWorkingHoursDoesNotModify(request);
    }

    @Test
    void replaceWorkingHours_whenStaffIsOutsideScope_throwsExceptionAndDoesNotModifyHours() {
        Long staffMemberId = 1L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(staffMemberId, businessId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> staffMemberService.replaceWorkingHours(staffMemberId, fullWeekRequest())
        );

        verify(staffWorkingHoursRepository, never()).deleteAllByStaffMemberId(anyLong());
        verify(staffWorkingHoursRepository, never()).saveAll(anyList());
    }

    private void assertDeleteBlockedByFutureAppointment(AppointmentStatus status) {
        Long id = 1L;
        Long businessId = 1L;
        StaffMember staffMember = StaffMember.builder()
                .id(id)
                .businessId(businessId)
                .status(StaffMemberStatus.ACTIVE)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(staffMember));
        when(appointmentRepository.existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
                eq(businessId),
                eq(id),
                eq(List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)),
                any(LocalDateTime.class)
        )).thenReturn(true);

        AppointmentOverlapException exception = assertThrows(
                AppointmentOverlapException.class,
                () -> staffMemberService.deleteStaffMember(id)
        );

        assertEquals(
                "Staff member cannot be deactivated because it has future active appointments",
                exception.getMessage()
        );
        assertEquals(StaffMemberStatus.ACTIVE, staffMember.getStatus());
        assertNotNull(status);

        verify(staffMemberRepository, never()).save(any());
        verify(staffMemberRepository, never()).deleteById(anyLong());
        verify(staffMemberRepository, never()).delete(any(StaffMember.class));
    }

    private void assertInvalidWorkingHoursDoesNotModify(List<StaffWorkingHoursRequestDto> request) {
        Long staffMemberId = 1L;
        Long businessId = 1L;
        StaffMember staffMember = StaffMember.builder()
                .id(staffMemberId)
                .businessId(businessId)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.findByIdAndBusinessId(staffMemberId, businessId))
                .thenReturn(Optional.of(staffMember));

        assertThrows(
                IllegalArgumentException.class,
                () -> staffMemberService.replaceWorkingHours(staffMemberId, request)
        );

        verify(staffWorkingHoursRepository, never()).deleteAllByStaffMemberId(anyLong());
        verify(staffWorkingHoursRepository, never()).saveAll(anyList());
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

    private StaffWorkingHoursRequestDto workingHoursRequest(DayOfWeek dayOfWeek) {
        return StaffWorkingHoursRequestDto.builder()
                .dayOfWeek(dayOfWeek)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(17, 0))
                .isAvailable(true)
                .build();
    }

    private StaffWorkingHours workingHoursEntity(Long staffMemberId, DayOfWeek dayOfWeek, boolean available) {
        return StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(dayOfWeek)
                .startsAt(available ? LocalTime.of(9, 0) : null)
                .endsAt(available ? LocalTime.of(18, 0) : null)
                .isAvailable(available)
                .build();
    }
}



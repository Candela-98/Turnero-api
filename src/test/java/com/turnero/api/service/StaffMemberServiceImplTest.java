package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.StaffMember;
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
}



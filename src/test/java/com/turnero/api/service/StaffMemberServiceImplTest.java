package com.turnero.api.service;

import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.StaffMember;
import com.turnero.api.repository.StaffMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void saveStaffMember_shouldSaveAndReturnStaffMember() {
        StaffMember staffMember = new StaffMember();
        staffMember.setName("Juan");
        staffMember.setSpecialty("Barber");

        when(staffMemberRepository.save(staffMember)).thenReturn(staffMember);

        StaffMember result = staffMemberService.saveStaffMember(staffMember);

        assertNotNull(result);
        assertEquals("Juan", result.getName());
        verify(staffMemberRepository, times(1)).save(staffMember);
        }

    @Test
    void findStaffMember_whenExists_returnsStaffMember() {
        Long id = 1L;
        StaffMember staffMember = new StaffMember();
        staffMember.setId(id);

        when(staffMemberRepository.findById(id)).thenReturn(Optional.of(staffMember));

        StaffMember result = staffMemberService.findStaffMember(id);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(staffMemberRepository, times(1)).findById(id);
    }

    @Test
    void findStaffMember_whenNotExists_throwsException() {
        Long id = 99L;
        when(staffMemberRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> staffMemberService.findStaffMember(id));

        assertEquals("Staffmember not found with ID: " + id, exception.getMessage());

        verify(staffMemberRepository, times(1)).findById(id);
    }

    @Test
    void listStaffMembers_shouldReturnList() {
        StaffMember p1 = new StaffMember();
        StaffMember p2 = new StaffMember();
        when(staffMemberRepository.findAll()).thenReturn(List.of(p1, p2));

        List<StaffMember> list = staffMemberService.findAllStaffMember();

        assertEquals(2, list.size());
        verify(staffMemberRepository, times(1)).findAll();
    }

    @Test
    void updateStaffMember_whenExists_updatesAndSaves() {
        Long id = 1L;

        StaffMember current = new StaffMember();
        current.setId(id);
        current.setName("Carlos");
        current.setSpecialty("Barber");

        StaffMember updatedStaff = new StaffMember();
        updatedStaff.setName("Juan Carlos");
        updatedStaff.setSpecialty("Barber plus");

        when(staffMemberRepository.findById(id)).thenReturn(Optional.of(current));

        staffMemberService.updateStaffMember(updatedStaff, id);

        verify(staffMemberRepository, times(1)).save(current);
        assertEquals("Juan Carlos", current.getName());
        assertEquals("Barber plus", current.getSpecialty());
    }

    @Test
    void updateStaffMember_whenDoesNotExist_throwsException_andDoesNotSave() {
        Long id = 99L;
        when(staffMemberRepository.findById(id)).thenReturn(Optional.empty());

        StaffMember updateStaff = new StaffMember();
        updateStaff.setName("New");

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> staffMemberService.updateStaffMember(updateStaff, id));

        assertEquals("Staffmember not found with ID: " + id, exception.getMessage());

        verify(staffMemberRepository, never()).save(any());
    }

    @Test
    void deleteStaffMember_whenExists_deletes() {
        Long id = 1L;
        when(staffMemberRepository.existsById(id)).thenReturn(true);

        staffMemberService.deleteStaffMember(id);

        verify(staffMemberRepository, times(1)).deleteById(id);
    }

    @Test
    void deleteStaffMember_whenDoesNotExist_throwsException() {
        Long id = 99L;
        when(staffMemberRepository.existsById(id)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> staffMemberService.deleteStaffMember(id));

        assertEquals("Staffmember not found with ID: " + id, exception.getMessage());

        verify(staffMemberRepository, never()).deleteById(anyLong());
    }
}


